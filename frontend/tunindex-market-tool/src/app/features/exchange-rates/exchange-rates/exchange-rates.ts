import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ExchangeRate } from '../../../core/services/exchange-rate';
import { CurrencyRate, ExchangeRates } from '../../../core/models/exchange-rate.model';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { CountryFlag } from '../../../shared/components/country-flag/country-flag';

/**
 * Live TND exchange rates — see core/services/exchange-rate.ts. Every rate
 * here comes from a real third-party FX feed (exchangerate-api.com) fetched
 * server-side; there is no fabricated daily-change figure since the
 * backend doesn't retain rate history yet.
 */
@Component({
  selector: 'app-exchange-rates',
  imports: [DecimalPipe, DatePipe, SkeletonBlock, EmptyState, CountryFlag],
  templateUrl: './exchange-rates.html',
  styleUrl: './exchange-rates.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExchangeRatesPage {
  private readonly exchangeRateService = inject(ExchangeRate);

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly data = signal<ExchangeRates | null>(null);

  protected readonly selectedCode = signal('EUR');
  protected readonly tndAmount = signal('1000');
  protected readonly foreignAmount = signal('');
  private lastEdited: 'tnd' | 'foreign' = 'tnd';

  protected readonly selectedRate = computed<CurrencyRate | null>(() => {
    const rates = this.data()?.rates ?? [];
    return rates.find((r) => r.code === this.selectedCode()) ?? null;
  });

  /**
   * Currency code to ISO 3166 country code, for the inline SVG flag.
   *
   * <p>These used to be regional-indicator emoji, which is the one approach
   * guaranteed to fail here: Windows ships no glyphs for them and renders
   * the bare letters ("CH", "JP"), which is most of this app's audience.
   * The SVG component was built for exactly this and simply was not wired in.
   */
  private static readonly FLAG_REGIONS: Record<string, string> = {
    EUR: 'EU',
    USD: 'US',
    GBP: 'GB',
    CHF: 'CH',
    JPY: 'JP',
    CAD: 'CA',
    CNY: 'CN',
    AED: 'AE',
    SAR: 'SA',
    MAD: 'MA',
    DZD: 'DZ',
    LYD: 'LY',
    EGP: 'EG',
    TND: 'TN',
  };

  /**
   * Region for a currency. Falls back to the code's first two letters, which
   * is the country for almost every ISO 4217 code; the flag component draws
   * a lettered tile for anything it does not have artwork for, so an unknown
   * currency degrades to a readable label rather than a blank cell.
   */
  protected flagFor(code: string): string {
    return ExchangeRatesPage.FLAG_REGIONS[code] ?? code.slice(0, 2).toUpperCase();
  }

  /** Round figures people actually convert, as a ready-reckoner. */
  private readonly quickAmounts = [1, 10, 100, 500, 1000, 5000, 10000];

  protected readonly quickConversions = computed(() => {
    const rate = this.selectedRate()?.rateToTnd;
    if (!rate) return [];
    return this.quickAmounts.map((tnd) => ({ tnd, foreign: tnd / rate }));
  });

  constructor() {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.exchangeRateService.getRates().subscribe({
      next: (res) => {
        this.data.set(res);
        this.loading.set(false);
        this.recompute();
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  protected onSelectCurrency(code: string): void {
    this.selectedCode.set(code);
    this.recompute();
  }

  protected onTndInput(value: string): void {
    this.tndAmount.set(value);
    this.lastEdited = 'tnd';
    this.recompute();
  }

  protected onForeignInput(value: string): void {
    this.foreignAmount.set(value);
    this.lastEdited = 'foreign';
    this.recompute();
  }

  private recompute(): void {
    const rate = this.selectedRate()?.rateToTnd;
    if (!rate) return;

    if (this.lastEdited === 'tnd') {
      const tnd = Number(this.tndAmount());
      this.foreignAmount.set(Number.isFinite(tnd) ? (tnd / rate).toFixed(2) : '');
    } else {
      const foreign = Number(this.foreignAmount());
      this.tndAmount.set(Number.isFinite(foreign) ? (foreign * rate).toFixed(2) : '');
    }
  }
}
