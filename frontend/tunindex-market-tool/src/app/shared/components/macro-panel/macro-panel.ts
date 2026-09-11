import { DecimalPipe } from '@angular/common';
import { AssetSymbol } from '../asset-symbol/asset-symbol';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MacroIndicator, MacroSnapshot, MarketQuote } from '../../../core/models/macro.model';
import { Market } from '../../../core/services/market';

/**
 * The Tunisian backdrop: what money costs, what it loses to inflation, what
 * the country owes, and what the dinar is worth.
 *
 * <p>Five figures, deliberately. This panel previously carried the policy
 * rate, the money-market rate, the savings rate and GDP growth as well — nine
 * cells, four of which had been identical to one another for the whole period
 * we hold data for. A banner earns attention by being short.
 */
@Component({
  selector: 'app-macro-panel',
  imports: [DecimalPipe, AssetSymbol],
  templateUrl: './macro-panel.html',
  styleUrl: './macro-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MacroPanel {
  private readonly market = inject(Market);

  protected readonly macro = signal<MacroSnapshot | null>(null);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);

  constructor() {
    this.market.getMacro().subscribe({
      next: (data) => {
        this.macro.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  /** Rates first, then the annual figures — slowest-moving last. */
  protected readonly indicators = computed<MacroIndicator[]>(() => {
    const data = this.macro();
    if (!data) {
      return [];
    }
    return [...(data.rates ?? []), ...(data.economy ?? [])];
  });

  protected readonly currencies = computed<MarketQuote[]>(() => this.macro()?.currencies ?? []);

  /** TMM is the hurdle every Tunisian asset is measured against, so it leads. */
  protected isHeadline(indicator: MacroIndicator): boolean {
    return indicator.key === 'TMM';
  }

  /** Annual series lag by up to a year and must not read as current. */
  protected isAnnual(indicator: MacroIndicator): boolean {
    return indicator.key === 'INFLATION_CPI' || indicator.key === 'EXTERNAL_DEBT_USD';
  }

  protected isMoney(indicator: MacroIndicator): boolean {
    return indicator.unit === 'USD';
  }

  /**
   * A 40-billion-dollar figure is unreadable in full and pointless to the
   * dollar, so it is shown in billions.
   */
  protected inBillions(value: number | null): number | null {
    return value === null ? null : value / 1_000_000_000;
  }

  /**
   * Inflation against the money-market rate. Above zero, cash holds its value
   * and equities have a real hurdle to clear; below, savers are pushed toward
   * risk. Null when either side is missing rather than guessed.
   */
  protected readonly realRate = computed<number | null>(() => {
    const tmm = this.macro()?.rates.find((r) => r.key === 'TMM')?.value;
    const inflation = this.macro()?.economy.find((r) => r.key === 'INFLATION_CPI')?.value;
    if (tmm === null || tmm === undefined || inflation === null || inflation === undefined) {
      return null;
    }
    return tmm - inflation;
  });
}
