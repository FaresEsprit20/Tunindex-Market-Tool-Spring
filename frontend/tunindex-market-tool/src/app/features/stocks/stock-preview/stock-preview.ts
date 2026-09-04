import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { StockDto } from '../../../core/models/stock.model';
import { PriceHistoryPoint } from '../../../core/models/price-history.model';
import { Stock } from '../../../core/services/stock';
import { CandlestickChart } from '../../../shared/components/candlestick-chart/candlestick-chart';
import { ScorePanel } from '../../../shared/components/score-panel/score-panel';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { MarketPulse } from '../../../shared/components/market-pulse/market-pulse';
import { TabbedPanel } from '../../../shared/components/tabbed-panel/tabbed-panel';
import { Tab } from '../../../shared/components/tabbed-panel/tab';
import { NewsList } from '../../../shared/components/news-list/news-list';
import { RiskPanel } from '../../../shared/components/risk-panel/risk-panel';

/**
 * The detail half of the quote-monitor split: whatever row is selected in
 * the grid, without leaving the grid.
 *
 * <p>This is the reason the split pane exists. Comparing two stocks
 * previously meant navigating away and back, losing scroll position and
 * filters each time; here the list stays put and only this side changes.
 */
@Component({
  selector: 'app-stock-preview',
  imports: [DecimalPipe, RouterLink, CandlestickChart, ScorePanel, SkeletonBlock, MarketPulse, TabbedPanel, Tab, NewsList, RiskPanel],
  templateUrl: './stock-preview.html',
  styleUrl: './stock-preview.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockPreview {
  /** Null shows the empty state rather than stale data from the last pick. */
  readonly symbol = input<string | null>(null);

  private readonly stockService = inject(Stock);

  protected readonly loading = signal(false);
  protected readonly stock = signal<StockDto | null>(null);
  protected readonly history = signal<PriceHistoryPoint[]>([]);
  protected readonly historyLoading = signal(false);

  constructor() {
    effect(() => {
      const symbol = this.symbol();
      if (!symbol) {
        this.stock.set(null);
        this.history.set([]);
        return;
      }

      this.loading.set(true);
      this.stockService.findBySymbol(symbol).subscribe({
        next: (res) => {
          this.stock.set(res);
          this.loading.set(false);
        },
        error: () => {
          this.stock.set(null);
          this.loading.set(false);
        },
      });

      this.historyLoading.set(true);
      this.stockService.getHistory(symbol, 180).subscribe({
        next: (points) => {
          this.history.set(points);
          this.historyLoading.set(false);
        },
        error: () => {
          this.history.set([]);
          this.historyLoading.set(false);
        },
      });
    });
  }

  protected dayChangePct(stock: StockDto): number | null {
    if (stock.lastPrice === null || stock.prevClose === null || stock.prevClose === 0) {
      return null;
    }
    return ((stock.lastPrice - stock.prevClose) / stock.prevClose) * 100;
  }
}
