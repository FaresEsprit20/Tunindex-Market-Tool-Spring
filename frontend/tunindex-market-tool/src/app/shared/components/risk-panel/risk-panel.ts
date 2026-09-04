import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { RiskMetrics } from '../../../core/models/risk.model';
import { Risk } from '../../../core/services/risk';

/**
 * Risk profile of one name: how much it moves, how far it has fallen, and how
 * much of that is the market rather than the company.
 *
 * <p>Reads figures straight from the server and never recomputes any of them
 * client-side, so this panel and any other view of the same statistic cannot
 * disagree. A null renders as an em dash — the server returns null wherever
 * the sample was too small, and dashing it is the honest rendering.
 */
@Component({
  selector: 'app-risk-panel',
  imports: [DecimalPipe],
  templateUrl: './risk-panel.html',
  styleUrl: './risk-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RiskPanel {
  readonly symbol = input.required<string>();

  /** Lookback in calendar days; the server clamps this to a sane range. */
  readonly windowDays = input(365);

  private readonly risk = inject(Risk);

  protected readonly metrics = signal<RiskMetrics | null>(null);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly showMethodology = signal(false);

  constructor() {
    effect(() => {
      const symbol = this.symbol();
      const window = this.windowDays();
      this.loading.set(true);
      this.failed.set(false);

      this.risk.getMetrics(symbol, window).subscribe({
        next: (data) => {
          this.metrics.set(data);
          this.loading.set(false);
        },
        error: () => {
          this.metrics.set(null);
          this.failed.set(true);
          this.loading.set(false);
        },
      });
    });
  }

  /**
   * True when the server declined to compute anything — it returns the shell
   * with methodology explaining why, rather than an error.
   */
  protected readonly insufficient = computed(() => {
    const data = this.metrics();
    return !!data && data.annualisedVolatilityPct === null;
  });

  /**
   * Volatility banded against what is normal on this exchange rather than a
   * textbook scale: BVMT names are thinner than developed-market equities, so
   * a US-calibrated "20% is low" would mark almost everything here as calm.
   */
  protected volatilityBand(value: number | null): string {
    if (value === null) {
      return '';
    }
    if (value < 15) {
      return 'low';
    }
    return value < 30 ? 'moderate' : 'high';
  }

  protected volatilityLabel(value: number | null): string {
    const band = this.volatilityBand(value);
    if (!band) {
      return '';
    }
    return band.charAt(0).toUpperCase() + band.slice(1);
  }

  /** Beta bands: below 1 moves less than the market, above 1 amplifies it. */
  protected betaLabel(beta: number | null): string {
    if (beta === null) {
      return '';
    }
    if (beta < 0) {
      return 'Moves against the market';
    }
    if (beta < 0.8) {
      return 'Steadier than the market';
    }
    return beta <= 1.2 ? 'Tracks the market' : 'Amplifies the market';
  }

  protected toggleMethodology(): void {
    this.showMethodology.update((open) => !open);
  }
}
