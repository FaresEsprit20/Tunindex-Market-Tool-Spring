import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ExchangeRate } from '../../../core/services/exchange-rate';
import { CurrencyRate, ExchangeRates } from '../../../core/models/exchange-rate.model';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';

/**
 * Live TND exchange rates — see core/services/exchange-rate.ts. Every rate
 * here comes from a real third-party FX feed (exchangerate-api.com) fetched
 * server-side; there is no fabricated daily-change figure since the
 * backend doesn't retain rate history yet.
 */
@Component({
  selector: 'app-exchange-rates',
  imports: [DecimalPipe, DatePipe, SkeletonBlock, EmptyState],
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
   * Currency code to country flag. Built from the ISO 3166 region letters
   * inside each currency code (EUR is the exception — a union, not a
   * country) and rendered as regional-indicator emoji, so there are no
   * image requests and nothing to 404.
   */
  private static readonly FLAGS: Record<string, string> = {
    EUR: '🇪🇺',
    USD: '🇺🇸',
    GBP: '🇬🇧',
    CHF: '🇨🇭',
    JPY: '🇯🇵',
    CAD: '🇨🇦',
    CNY: '🇨🇳',
    AED: '🇦🇪',
    SAR: '🇸🇦',
    MAD: '🇲🇦',
    DZD: '🇩🇿',
    LYD: '🇱🇾',
    EGP: '🇪🇬',
    TRY: '🇹🇷',
    SEK: '🇸🇪',
    NOK: '🇳🇴',
    DKK: '🇩🇰',
    RUB: '🇷🇺',
    INR: '🇮🇳',
    AUD: '🇦🇺',
    KWD: '🇰🇼',
    QAR: '🇶🇦',
    BHD: '🇧🇭',
    JOD: '🇯🇴',
    TND: '🇹🇳',
  };

  protected flagFor(code: string): string {
    const known = ExchangeRatesPage.FLAGS[code];
    if (known) {
      return known;
    }
    // Fall back to the code's first two letters as regional indicators —
    // correct for most currencies, since the first two letters are the
    // country. A wrong-looking flag beats a missing cell.
    const region = code.slice(0, 2).toUpperCase();
    if (!/^[A-Z]{2}$/.test(region)) {
      return '🏳️';
    }
    return String.fromCodePoint(
      ...[...region].map((letter) => 0x1f1e6 + letter.charCodeAt(0) - 65),
    );
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
