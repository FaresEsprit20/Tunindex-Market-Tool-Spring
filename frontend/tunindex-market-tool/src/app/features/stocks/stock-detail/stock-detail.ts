import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OWNERSHIP_LABELS, SECTOR_LABELS, StockDto } from '../../../core/models/stock.model';
import { Stock } from '../../../core/services/stock';
import { Notification } from '../../../core/services/notification';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { StatTile } from '../../../shared/components/stat-tile/stat-tile';
import { RangeBar } from '../../../shared/components/range-bar/range-bar';
import { WatchlistStar } from '../../../shared/components/watchlist-star/watchlist-star';
import { CandlestickChart } from '../../../shared/components/candlestick-chart/candlestick-chart';
import { PriceHistoryPoint } from '../../../core/models/price-history.model';
import { NewsList } from '../../../shared/components/news-list/news-list';
import { ScorePanel } from '../../../shared/components/score-panel/score-panel';
import { exchangeCountry } from '../../../core/constants/exchange-flags';
import { CountryFlag } from '../../../shared/components/country-flag/country-flag';
import { Explain } from '../../../shared/directives/explain';
import {
  EXPLAIN_NOTES,
  explainDayChange,
  explainMarginOfSafety,
  explainPeRatio,
  explainPositionIn52Week,
  explainPriceToBook,
} from '../../../core/utils/explain-formulas';

@Component({
  selector: 'app-stock-detail',
  imports: [RouterLink, DecimalPipe, SkeletonBlock, StatTile, RangeBar, WatchlistStar, CandlestickChart, NewsList, ScorePanel, Explain, CountryFlag],
  templateUrl: './stock-detail.html',
  styleUrl: './stock-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockDetail {
  protected readonly exchangeCountry = exchangeCountry;
  // Exposed to the template so each figure can show its own arithmetic.
  protected readonly notes = EXPLAIN_NOTES;
  protected readonly explainPeRatio = explainPeRatio;
  protected readonly explainPriceToBook = explainPriceToBook;
  protected readonly explainMarginOfSafety = explainMarginOfSafety;
  protected readonly explainDayChange = explainDayChange;
  protected readonly explainPositionIn52Week = explainPositionIn52Week;
  private readonly stockService = inject(Stock);
  private readonly notification = inject(Notification);
  private readonly route = inject(ActivatedRoute);

  protected readonly sectorLabels = SECTOR_LABELS;
  protected readonly ownershipLabels = OWNERSHIP_LABELS;

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly refreshing = signal(false);
  protected readonly stock = signal<StockDto | null>(null);

  protected readonly historyLoading = signal(true);
  protected readonly history = signal<PriceHistoryPoint[]>([]);

  private readonly paramMap = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  protected readonly symbol = computed(() => this.paramMap().get('symbol') ?? '');

  protected readonly dayChangePct = computed(() => {
    const s = this.stock();
    if (!s || s.lastPrice === null || s.prevClose === null || s.prevClose === 0) {
      return null;
    }
    return ((s.lastPrice - s.prevClose) / s.prevClose) * 100;
  });

  constructor() {
    // Angular reuses this component instance when navigating between two
    // /app/stocks/:symbol URLs (same route config, different param) — a
    // constructor-only fetch would leave the previous symbol's data on
    // screen until a full page reload. Track symbol() explicitly so every
    // navigation, not just the first mount, triggers a reload.
    effect(() => {
      this.symbol();
      this.load();
    });
  }

  private load(): void {
    const symbol = this.symbol();
    if (!symbol) {
      this.error.set(true);
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(false);

    this.stockService.findBySymbol(symbol).subscribe({
      next: (res) => {
        this.stock.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });

    this.historyLoading.set(true);
    this.stockService.getHistory(symbol).subscribe({
      next: (points) => {
        this.history.set(points);
        this.historyLoading.set(false);
      },
      error: () => {
        this.history.set([]);
        this.historyLoading.set(false);
      },
    });
  }

  protected refresh(): void {
    const symbol = this.symbol();
    if (!symbol || this.refreshing()) {
      return;
    }
    this.refreshing.set(true);

    this.stockService.refresh(symbol).subscribe({
      next: () => {
        this.refreshing.set(false);
        this.notification.show('Refresh triggered', `${symbol} data will update shortly.`, 'success');
      },
      error: () => {
        this.refreshing.set(false);
        this.notification.show('Refresh failed', 'Could not trigger a data refresh.', 'error');
      },
    });
  }
}
