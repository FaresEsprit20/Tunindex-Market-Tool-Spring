import { DecimalPipe } from '@angular/common';
import { AssetSymbol } from '../asset-symbol/asset-symbol';
import { AnimatedNumber } from '../animated-number/animated-number';
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
  imports: [DecimalPipe, AssetSymbol, AnimatedNumber],
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
   * The technology funds and MicroStrategy.
   *
   * <p>Grouped apart from the metals because they answer a different
   * question - metals are what a Tunisian investor holds *instead* of
   * equities, while these are the equity risk they are being compared
   * against. Same close-to-close basis, so the numbers are comparable.
   */
  protected readonly equities = computed(() =>
    this.quotes().filter((quote) => quote.category === 'EQUITY'),
  );

  /** Whether anything at all arrived, for the empty state. */
  protected readonly hasAny = computed(() => this.quotes().length > 0);

  /**
   * Decimals appropriate to the instrument's own scale.
   *
   * <p>Bitcoin's cents are noise at seventy-odd thousand; two cents on silver
   * is a real move. An ETF trading near 100 wants cents.
   */
  protected digitsFor(quote: MarketQuote): string {
    if (quote.category === 'CRYPTO' && quote.price > 1000) {
      return '1.0-0';
    }
    return '1.2-2';
  }

  /** Spelled out, so the two bases in this banner are never conflated. */
  protected basisLabel(quote: MarketQuote): string {
    return quote.changeBasis === 'ROLLING_24H'
      ? 'Change over the last 24 hours'
      : 'Change against the previous close of ' + (quote.previousClose ?? '—');
  }
}
