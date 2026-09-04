import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MacroIndicator, MacroSnapshot } from '../../../core/models/macro.model';
import { Market } from '../../../core/services/market';

/**
 * The macro backdrop every valuation on this platform is implicitly quoted
 * against: what cash pays, and what inflation takes away.
 *
 * <p>These are the figures that decide whether Tunisian equities are worth
 * holding at all. A 7% policy rate is the hurdle every stock here has to
 * clear, and the dashboard was previously silent about it.
 *
 * <p>Every figure carries the period its publisher stated and the publisher's
 * name. That is not decoration: the rates are current, the inflation and
 * growth figures are annual and may be a year old, and a reader has to be
 * able to tell those apart at a glance.
 */
@Component({
  selector: 'app-macro-panel',
  imports: [DecimalPipe],
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

  /**
   * The policy rate gets the lead position — it is the single number that
   * sets the hurdle for every other asset in the country.
   */
  protected isHeadline(indicator: MacroIndicator): boolean {
    return indicator.key === 'POLICY_RATE';
  }

  /**
   * Inflation above the policy rate means cash loses value in real terms,
   * which is the case for equities in one line. Returns null when either
   * side is missing rather than guessing.
   */
  protected realRate(): number | null {
    const policy = this.macro()?.rates.find((r) => r.key === 'POLICY_RATE')?.value;
    const inflation = this.macro()?.economy.find((r) => r.key === 'INFLATION_CPI')?.value;
    if (policy === null || policy === undefined || inflation === null || inflation === undefined) {
      return null;
    }
    return policy - inflation;
  }
}
