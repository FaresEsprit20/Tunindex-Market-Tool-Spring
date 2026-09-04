import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { Theme } from '../../../core/services/theme';
import { DENSITY_OPTIONS, Density, DensityMode } from '../../../core/services/density';
import { Stock } from '../../../core/services/stock';
import { StockDto } from '../../../core/models/stock.model';
import { PulseDot } from '../pulse-dot/pulse-dot';
import { NotificationBell } from '../notification-bell/notification-bell';
import { SessionClock } from '../session-clock/session-clock';

const RESULT_LIMIT = 6;
const FRESHNESS_REFRESH_INTERVAL_MS = 60_000;

@Component({
  selector: 'app-navbar',
  imports: [PulseDot, NotificationBell, SessionClock],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Navbar {
  protected readonly theme = inject(Theme);
  protected readonly density = inject(Density);
  protected readonly densityOptions = DENSITY_OPTIONS;
  private readonly router = inject(Router);
  private readonly stockService = inject(Stock);
  private readonly elementRef = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  // Mac reports "MacIntel"/"Mac" in platform; everything else gets Ctrl.
  protected readonly shortcutHint = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘K' : 'Ctrl K';

  protected readonly searchQuery = signal('');
  protected readonly results = signal<StockDto[]>([]);
  protected readonly searching = signal(false);
  protected readonly dropdownOpen = signal(false);

  private readonly lastUpdateAt = signal<Date | null>(null);
  protected readonly freshness = computed<'live' | 'recent' | 'stale' | 'unknown'>(() => {
    const at = this.lastUpdateAt();
    if (!at) return 'unknown';
    const minutesAgo = (Date.now() - at.getTime()) / 60000;
    if (minutesAgo < 60) return 'live';
    if (minutesAgo < 60 * 24) return 'recent';
    return 'stale';
  });
  protected readonly freshnessLabel = computed(() => {
    const at = this.lastUpdateAt();
    if (!at) return 'Checking data…';
    const minutesAgo = Math.round((Date.now() - at.getTime()) / 60000);
    if (minutesAgo < 1) return 'Updated just now';
    if (minutesAgo < 60) return `Updated ${minutesAgo}m ago`;
    const hoursAgo = Math.round(minutesAgo / 60);
    if (hoursAgo < 24) return `Updated ${hoursAgo}h ago`;
    return `Updated ${Math.round(hoursAgo / 24)}d ago`;
  });

  private readonly queryChanges = new Subject<string>();

  constructor() {
    // Real freshness, not a permanently-on "live" label: the most recent
    // lastUpdate timestamp across every tracked stock, from the same
    // backend field the collector stamps on every save. The navbar stays
    // mounted for the whole session, so this is re-fetched on an interval
    // rather than once — otherwise "Updated Xm ago" would freeze at
    // whatever it read at login and drift further wrong the longer the
    // user stays on the page.
    this.loadFreshness();
    const freshnessIntervalId = setInterval(() => this.loadFreshness(), FRESHNESS_REFRESH_INTERVAL_MS);
    this.destroyRef.onDestroy(() => clearInterval(freshnessIntervalId));

    this.queryChanges
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        switchMap((query) => {
          if (query.length < 1) {
            return of({ bySymbol: [] as StockDto[], byName: [] as StockDto[] });
          }
          this.searching.set(true);
          const bySymbol = this.stockService
            .filter({ page: 1, size: RESULT_LIMIT, filters: { symbol: query } })
            .pipe(
              map((res) => res.content),
              catchError(() => of<StockDto[]>([])),
            );
          const byName = this.stockService
            .filter({ page: 1, size: RESULT_LIMIT, filters: { name: query } })
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
        for (const s of [...bySymbol, ...byName]) {
          merged.set(s.id, s);
        }
        this.results.set([...merged.values()].slice(0, RESULT_LIMIT));
        this.searching.set(false);
      });

    document.addEventListener('click', this.onDocumentClick, { capture: true });
    this.destroyRef.onDestroy(() => document.removeEventListener('click', this.onDocumentClick, { capture: true }));
  }

  private loadFreshness(): void {
    this.stockService
      .filter({ page: 1, size: 1, sortField: 'lastUpdate', sortDirection: 'DESC' })
      .subscribe({
        next: (res) => {
          const latest = res.content[0]?.lastUpdate;
          this.lastUpdateAt.set(latest ? new Date(latest) : null);
        },
        error: () => this.lastUpdateAt.set(null),
      });
  }

  private readonly onDocumentClick = (event: MouseEvent): void => {
    if (!this.elementRef.nativeElement.contains(event.target as Node)) {
      this.dropdownOpen.set(false);
    }
  };

  protected onSearchFocus(): void {
    if (this.searchQuery().trim().length > 0) {
      this.dropdownOpen.set(true);
    }
  }

  protected onQueryInput(value: string): void {
    this.searchQuery.set(value);
    this.dropdownOpen.set(value.trim().length > 0);
    this.queryChanges.next(value.trim());
  }

  protected onSearchSubmit(): void {
    const query = this.searchQuery().trim();
    if (!query) {
      return;
    }
    this.dropdownOpen.set(false);
    void this.router.navigate(['/app/stocks'], { queryParams: { q: query } });
  }

  protected openStock(symbol: string): void {
    this.dropdownOpen.set(false);
    this.searchQuery.set('');
    void this.router.navigate(['/app/stocks', symbol]);
  }

  /** Steps through the density levels in order, wrapping at the end. */
  protected cycleDensity(): void {
    const order: DensityMode[] = ['comfortable', 'compact', 'terminal'];
    const next = order[(order.indexOf(this.density.mode()) + 1) % order.length];
    this.density.set(next);
  }

  protected densityLabel(): string {
    return this.densityOptions.find((o) => o.value === this.density.mode())?.label ?? '';
  }

  protected onThemeToggle(event: MouseEvent): void {
    this.theme.toggle({ x: event.clientX, y: event.clientY });
  }
}
