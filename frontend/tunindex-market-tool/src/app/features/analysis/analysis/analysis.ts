import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Stock } from '../../../core/services/stock';
import { StockDto } from '../../../core/models/stock.model';
import { TechnicalAnalysis, FundamentalAnalysis } from '../../../core/models/analysis.model';
import { PriceHistoryPoint } from '../../../core/models/price-history.model';
import { RangeBar } from '../../../shared/components/range-bar/range-bar';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { NewsList } from '../../../shared/components/news-list/news-list';
import { CandlestickChart } from '../../../shared/components/candlestick-chart/candlestick-chart';

@Component({
  selector: 'app-analysis',
  imports: [DecimalPipe, RangeBar, SkeletonBlock, EmptyState, NewsList, CandlestickChart],
  templateUrl: './analysis.html',
  styleUrl: './analysis.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Analysis {
  private readonly stockService = inject(Stock);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly paramMap = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  protected readonly symbol = computed(() => this.paramMap().get('symbol') ?? '');

  protected readonly searchInput = signal('');

  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly stock = signal<StockDto | null>(null);

  /**
   * Day move against the previous close, in both dinars and percent.
   *
   * <p>This page showed a bare price and nothing else, which on a page about
   * a stock's condition is the one omission a reader notices — every quote
   * page anywhere puts the change right next to the price. Returns null
   * rather than zero when either side is missing, so an unpriced name reads
   * as unknown instead of flat.
   */
  protected readonly dayChange = computed(() => {
    const s = this.stock();
    if (!s || s.lastPrice === null || s.prevClose === null || s.prevClose === 0) {
      return null;
    }
    const delta = s.lastPrice - s.prevClose;
    return { delta, pct: (delta / s.prevClose) * 100 };
  });
  protected readonly technical = signal<TechnicalAnalysis | null>(null);
  protected readonly fundamental = signal<FundamentalAnalysis | null>(null);
  protected readonly history = signal<PriceHistoryPoint[]>([]);
  protected readonly historyLoading = signal(true);

  constructor() {
    // Same route config (analysis/:symbol) is reused across searches, so a
    // constructor-only fetch would strand the previous symbol's data on
    // screen after a new search — track symbol() so every change reloads.
    effect(() => {
      this.symbol();
      this.load();
    });
  }

  private load(): void {
    const symbol = this.symbol();
    if (!symbol) {
      return;
    }
    this.searchInput.set(symbol);
    this.loading.set(true);
    this.error.set(false);
    this.stock.set(null);
    this.technical.set(null);
    this.fundamental.set(null);
    this.history.set([]);
    this.historyLoading.set(true);

    this.stockService.findBySymbol(symbol).subscribe({
      next: (res) => this.stock.set(res),
      error: () => this.error.set(true),
    });

    this.stockService.getHistory(symbol).subscribe({
      next: (res) => {
        this.history.set(res);
        this.historyLoading.set(false);
      },
      error: () => this.historyLoading.set(false),
    });

    this.stockService.getTechnicalAnalysis(symbol).subscribe({
      next: (res) => this.technical.set(res),
      error: () => {},
    });

    this.stockService.getFundamentalAnalysis(symbol).subscribe({
      next: (res) => {
        this.fundamental.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  protected onSearchSubmit(): void {
    const query = this.searchInput().trim().toUpperCase();
    if (!query) return;
    void this.router.navigate(['/app/analysis', query]);
  }

  protected scoreLabel(score: number): string {
    if (score >= 70) return 'Strong';
    if (score >= 45) return 'Moderate';
    return 'Weak';
  }

  protected ratingClass(rating: string): string {
    if (rating === 'STRONG') return 'positive';
    if (rating === 'WEAK') return 'negative';
    return 'warning';
  }

  protected trendClass(signal: string): string {
    if (signal === 'BULLISH') return 'positive';
    if (signal === 'BEARISH') return 'negative';
    return 'neutral';
  }

  protected adxClass(signal: string): string {
    if (signal === 'STRONG_TREND') return 'positive';
    if (signal === 'WEAK_TREND') return 'warning';
    return 'neutral';
  }
}
