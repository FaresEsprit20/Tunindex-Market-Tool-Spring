import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { Theme } from '../../../core/services/theme';
import { Stock } from '../../../core/services/stock';
import { StockDto } from '../../../core/models/stock.model';
import { PulseDot } from '../pulse-dot/pulse-dot';

const RESULT_LIMIT = 6;

@Component({
  selector: 'app-navbar',
  imports: [PulseDot],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Navbar {
  protected readonly theme = inject(Theme);
  private readonly router = inject(Router);
  private readonly stockService = inject(Stock);
  private readonly elementRef = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly searchQuery = signal('');
  protected readonly results = signal<StockDto[]>([]);
  protected readonly searching = signal(false);
  protected readonly dropdownOpen = signal(false);

  private readonly queryChanges = new Subject<string>();

  constructor() {
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
}
