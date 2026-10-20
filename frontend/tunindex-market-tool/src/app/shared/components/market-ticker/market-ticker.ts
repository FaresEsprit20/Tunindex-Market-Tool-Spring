import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Stock } from '../../../core/services/stock';

interface TickerItem {
  symbol: string;
  price: string;
  changePct: number;
  /**
   * Whether this symbol's price moved on the most recent refresh, and
   * which way. Drives a one-shot tick flash so a real change is visible
   * even on a strip that is always in motion. Null on first load — nothing
   * to compare against, and flashing every symbol at once would be noise.
   */
  tick: 'up' | 'down' | null;
}

const REFRESH_INTERVAL_MS = 60_000;

/**
 * Continuously scrolling market strip, driven by real prices — every symbol
 * shown here comes from a live GET .../filter call against the backend, not
 * placeholder data. Only rendered inside the authenticated app shell, since
 * the stock endpoints require a session; there is no public equivalent to
 * feed this on the pre-login screens.
 *
 * The app shell (and this component with it) stays mounted for the whole
 * session — it never gets destroyed/recreated by route navigation the way
 * page components do — so a one-shot constructor fetch would freeze the
 * strip at whatever prices existed at login. Refreshed on an interval
 * instead so it stays live while the user browses.
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
  private readonly destroyRef = inject(DestroyRef);

  private readonly loadedItems = signal<TickerItem[]>([]);
  protected readonly items = computed(() => this.loadedItems());

  constructor() {
    this.load();
    const intervalId = setInterval(() => this.load(), REFRESH_INTERVAL_MS);
    this.destroyRef.onDestroy(() => clearInterval(intervalId));
  }

  private load(): void {
    this.stockService.filter({ page: 1, size: 40, sortField: 'symbol', sortDirection: 'ASC' }).subscribe({
      next: (res) => {
        const previous = new Map(this.loadedItems().map((item) => [item.symbol, item.price]));
        const isFirstLoad = previous.size === 0;

        const items: TickerItem[] = res.content
          .filter((s) => s.lastPrice !== null && s.prevClose !== null && s.prevClose !== 0)
          .map((s) => {
            const price = s.lastPrice!.toFixed(2);
            const before = previous.get(s.symbol);
            let tick: 'up' | 'down' | null = null;
            if (!isFirstLoad && before !== undefined && before !== price) {
              tick = Number(price) > Number(before) ? 'up' : 'down';
            }
            return {
              symbol: s.symbol,
              price,
              changePct: Math.round(((s.lastPrice! - s.prevClose!) / s.prevClose!) * 10000) / 100,
              tick,
            };
          });
        this.loadedItems.set(items);

        // Clear the flags once the flash has played, so the animation is a
        // one-shot on the change rather than a permanent state on the row.
        if (items.some((item) => item.tick !== null)) {
          const clearId = setTimeout(() => {
            this.loadedItems.update((current) => current.map((item) => ({ ...item, tick: null })));
          }, 1600);
          this.destroyRef.onDestroy(() => clearTimeout(clearId));
        }
      },
      error: () => this.loadedItems.set([]),
    });
  }
}
