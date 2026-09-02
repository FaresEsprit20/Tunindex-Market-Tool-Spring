import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { NAV_ITEMS } from '../../../core/constants/nav-items';
import { StockDto } from '../../../core/models/stock.model';
import { Auth } from '../../../core/services/auth';
import { Notification } from '../../../core/services/notification';
import { Stock } from '../../../core/services/stock';
import { Theme } from '../../../core/services/theme';

const STOCK_RESULT_LIMIT = 6;

/** A flat, uniformly-navigable row: every entry is one arrow-key stop. */
interface PaletteRow {
  kind: 'page' | 'action' | 'stock';
  id: string;
  label: string;
  hint?: string;
  icon: string;
  run: () => void;
}

/**
 * Global ⌘K / Ctrl+K launcher — jump to any page, any stock, or run an
 * action without touching the mouse. Mounted once in the app shell, so it
 * is reachable from every authenticated screen. Stock rows come from the
 * same real backend search the navbar uses (symbol + name, merged).
 */
@Component({
  selector: 'app-command-palette',
  imports: [],
  templateUrl: './command-palette.html',
  styleUrl: './command-palette.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPalette {
  private readonly router = inject(Router);
  private readonly stockService = inject(Stock);
  private readonly theme = inject(Theme);
  private readonly auth = inject(Auth);
  private readonly notification = inject(Notification);
  private readonly destroyRef = inject(DestroyRef);

  private readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  protected readonly open = signal(false);
  protected readonly query = signal('');
  protected readonly activeIndex = signal(0);
  protected readonly searching = signal(false);
  private readonly stockResults = signal<StockDto[]>([]);

  private readonly queryChanges = new Subject<string>();

  /** Static rows: navigation targets plus app-level actions. */
  private readonly staticRows = computed<PaletteRow[]>(() => [
    ...NAV_ITEMS.map((item) => ({
      kind: 'page' as const,
      id: `page:${item.route}`,
      label: item.label,
      icon: item.icon,
      run: () => void this.router.navigateByUrl(item.route),
    })),
    {
      kind: 'action',
      id: 'action:account',
      label: 'Account settings',
      icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 20c0-3.3 3.6-6 8-6s8 2.7 8 6',
      run: () => void this.router.navigateByUrl('/app/account'),
    },
    {
      kind: 'action',
      id: 'action:theme',
      label: this.theme.mode() === 'dark' ? 'Switch to light theme' : 'Switch to dark theme',
      hint: 'Appearance',
      icon: 'M20.5 14.5A8.5 8.5 0 1 1 9.5 3.5a7 7 0 0 0 11 11z',
      run: () => this.theme.toggle(),
    },
    {
      kind: 'action',
      id: 'action:logout',
      label: 'Sign out',
      icon: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
      run: () => this.signOut(),
    },
  ]);

  /** Static rows filtered by the query, then live stock matches appended. */
  protected readonly pageRows = computed<PaletteRow[]>(() => {
    const query = this.query().trim().toLowerCase();
    if (!query) return this.staticRows();
    return this.staticRows().filter((row) => row.label.toLowerCase().includes(query));
  });

  protected readonly stockRows = computed<PaletteRow[]>(() =>
    this.stockResults().map((stock) => ({
      kind: 'stock' as const,
      id: `stock:${stock.id}`,
      label: stock.symbol,
      hint: stock.name,
      icon: 'M3 3v18h18M7 14l4-4 3 3 5-6',
      run: () => void this.router.navigate(['/app/stocks', stock.symbol]),
    })),
  );

  /** One flat list so ArrowUp/ArrowDown crosses section boundaries. */
  protected readonly allRows = computed<PaletteRow[]>(() => [...this.pageRows(), ...this.stockRows()]);

  constructor() {
    // Focus the input once it actually exists. viewChild is a signal, so
    // this effect re-runs when the @if block renders and resolves it — a
    // queueMicrotask here would fire before that render and silently no-op,
    // leaving every keystroke going nowhere.
    effect(() => {
      if (!this.open()) return;
      this.searchInput()?.nativeElement.focus();
    });

    // Keep the highlighted row in range as results stream in and change the
    // list length underneath it.
    effect(() => {
      const count = this.allRows().length;
      if (this.activeIndex() >= count) {
        this.activeIndex.set(Math.max(0, count - 1));
      }
    });

    this.queryChanges
      .pipe(
        debounceTime(180),
        distinctUntilChanged(),
        switchMap((query) => {
          if (query.length < 1) {
            this.searching.set(false);
            return of({ bySymbol: [] as StockDto[], byName: [] as StockDto[] });
          }
          this.searching.set(true);
          const bySymbol = this.stockService
            .filter({ page: 1, size: STOCK_RESULT_LIMIT, filters: { symbol: query } })
            .pipe(
              map((res) => res.content),
              catchError(() => of<StockDto[]>([])),
            );
          const byName = this.stockService
            .filter({ page: 1, size: STOCK_RESULT_LIMIT, filters: { name: query } })
            .pipe(
              map((res) => res.content),
              catchError(() => of<StockDto[]>([])),
            );
          return forkJoin({ bySymbol, byName });
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(({ bySymbol, byName }) => {
        const merged = new Map<number, StockDto>();
        for (const stock of [...bySymbol, ...byName]) {
          merged.set(stock.id, stock);
        }
        this.stockResults.set([...merged.values()].slice(0, STOCK_RESULT_LIMIT));
        this.searching.set(false);
      });

    window.addEventListener('keydown', this.onGlobalKeydown);
    this.destroyRef.onDestroy(() => window.removeEventListener('keydown', this.onGlobalKeydown));
  }

  private readonly onGlobalKeydown = (event: KeyboardEvent): void => {
    // ⌘K on macOS, Ctrl+K elsewhere. Browsers bind Ctrl+K to the address
    // bar, so preventDefault is required for the shortcut to reach us.
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.open() ? this.close() : this.openPalette();
      return;
    }

    if (!this.open()) return;

    switch (event.key) {
      case 'Escape':
        event.preventDefault();
        this.close();
        break;
      case 'ArrowDown':
        event.preventDefault();
        this.move(1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.move(-1);
        break;
      case 'Enter': {
        event.preventDefault();
        this.runRow(this.allRows()[this.activeIndex()]);
        break;
      }
    }
  };

  private move(delta: number): void {
    const count = this.allRows().length;
    if (count === 0) return;
    // Wrap around at both ends, the way every good launcher behaves.
    this.activeIndex.set((this.activeIndex() + delta + count) % count);
    this.scrollActiveIntoView();
  }

  private scrollActiveIntoView(): void {
    queueMicrotask(() => {
      document
        .querySelector('.palette-row.active')
        ?.scrollIntoView({ block: 'nearest' });
    });
  }

  protected openPalette(): void {
    this.query.set('');
    this.stockResults.set([]);
    this.activeIndex.set(0);
    this.open.set(true);
    // Focus is handled by the effect in the constructor, which waits for the
    // input element to actually exist.
  }

  protected close(): void {
    this.open.set(false);
  }

  protected onQueryInput(value: string): void {
    this.query.set(value);
    this.activeIndex.set(0);
    this.queryChanges.next(value.trim());
  }

  protected runRow(row: PaletteRow | undefined): void {
    if (!row) return;
    this.close();
    row.run();
  }

  private signOut(): void {
    this.auth.logout().subscribe({
      next: () => {
        this.notification.show('Signed out', "You've been signed out.", 'success');
        void this.router.navigateByUrl('/auth/login');
      },
      error: () => this.notification.show('Sign out failed', 'Please try again.', 'error'),
    });
  }
}
