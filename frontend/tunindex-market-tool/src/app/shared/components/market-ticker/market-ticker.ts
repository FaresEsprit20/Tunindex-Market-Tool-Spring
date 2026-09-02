import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Stock } from '../../../core/services/stock';

interface TickerItem {
  symbol: string;
  price: string;
  changePct: number;
}

/**
 * Continuously scrolling market strip, driven by real prices — every symbol
 * shown here comes from a live GET .../filter call against the backend (see
 * constructor), not placeholder data. Only rendered inside the authenticated
 * app shell, since the stock endpoints require a session; there is no public
 * equivalent to feed this on the pre-login screens.
 */
@Component({
  selector: 'app-market-ticker',
  imports: [],
  templateUrl: './market-ticker.html',
  styleUrl: './market-ticker.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'market-ticker-host' },
})
export class MarketTicker {
  private readonly stockService = inject(Stock);

  private readonly loadedItems = signal<TickerItem[]>([]);
  protected readonly items = computed(() => this.loadedItems());

  constructor() {
    this.stockService.filter({ page: 1, size: 40, sortField: 'symbol', sortDirection: 'ASC' }).subscribe({
      next: (res) => {
        const items = res.content
          .filter((s) => s.lastPrice !== null && s.prevClose !== null && s.prevClose !== 0)
          .map((s) => ({
            symbol: s.symbol,
            price: s.lastPrice!.toFixed(2),
            changePct: Math.round(((s.lastPrice! - s.prevClose!) / s.prevClose!) * 10000) / 100,
          }));
        this.loadedItems.set(items);
      },
      error: () => this.loadedItems.set([]),
    });
  }
}
