import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MarketQuote } from '../../../core/models/macro.model';
import { Market } from '../../../core/services/market';

/**
 * Gold, silver and the major crypto pairs — the risk assets a Tunisian
 * investor is choosing between when they choose equities.
 *
 * <p>The metals and the coins do not share a definition of "daily change":
 * metals have an exchange close to measure against, while crypto trades
 * continuously and every venue quotes a rolling 24-hour window instead. Each
 * quote carries its own {@code changeBasis} and the tooltip says which, rather
 * than presenting two different measurements as though they were one.
 */
@Component({
  selector: 'app-commodities-banner',
  imports: [DecimalPipe],
  templateUrl: './commodities-banner.html',
  styleUrl: './commodities-banner.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommoditiesBanner {
  private readonly market = inject(Market);

  protected readonly quotes = signal<MarketQuote[]>([]);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);

  constructor() {
    this.market.getCommodities().subscribe({
      next: (data) => {
        this.quotes.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  protected readonly metals = computed(() =>
    this.quotes().filter((quote) => quote.category === 'METAL'),
  );

  protected readonly crypto = computed(() =>
    this.quotes().filter((quote) => quote.category === 'CRYPTO'),
  );

  /**
   * Crypto is quoted to the dollar and metals to the cent: bitcoin's cents are
   * noise at seventy-odd thousand, while two cents on silver is a real move.
   */
  protected digitsFor(quote: MarketQuote): string {
    return quote.category === 'CRYPTO' && quote.price > 1000 ? '1.0-0' : '1.2-2';
  }

  /** Spelled out, so the two bases in this banner are never conflated. */
  protected basisLabel(quote: MarketQuote): string {
    return quote.changeBasis === 'ROLLING_24H'
      ? 'Change over the last 24 hours'
      : 'Change against the previous close of ' + (quote.previousClose ?? '—');
  }
}
