import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Market } from '../../../core/services/market';
import { AnimatedNumber } from '../../../shared/components/animated-number/animated-number';

interface PulseStats {
  avgChangePct: number;
  advancing: number;
  declining: number;
  unchanged: number;
  /** Shares traded across every priced name today. */
  totalVolume: number;
}

/**
 * "Pulse" = genuine aggregates over every tracked stock's real lastPrice vs
 * prevClose — not a market index feed, which BVMT doesn't expose here. The
 * line graphic below it is a fixed decorative flourish (same technique as the
 * login hero), not a chart of this data — it carries no axis or values, so it
 * can't misread as one.
 *
 * <p>These figures come from GET /market/breadth rather than being recomputed
 * here from the stock list. Two independent implementations of "advancing"
 * had already drifted apart: this one counted every row, including symbols
 * whose exchange page we can no longer read at all, so the dashboard and the
 * stocks page disagreed about how many names were up.
 */
@Component({
  selector: 'app-market-pulse',
  imports: [AnimatedNumber],
  templateUrl: './market-pulse.html',
  styleUrl: './market-pulse.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketPulse {
  private readonly market = inject(Market);

  protected readonly loading = signal(true);
  private readonly stats = signal<PulseStats | null>(null);

  protected readonly pulse = computed(() => this.stats());

  constructor() {
    this.market.getBreadth().subscribe({
      next: (breadth) => {
        this.stats.set({
          avgChangePct: breadth.averageChangePct ?? 0,
          advancing: breadth.advancing,
          declining: breadth.declining,
          unchanged: breadth.unchanged,
          // Volume, not market cap: the breadth endpoint reports session
          // activity, and summing a partial set of market caps produced a
          // headline figure that silently omitted every name without one.
          totalVolume: breadth.totalVolume ?? 0,
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
