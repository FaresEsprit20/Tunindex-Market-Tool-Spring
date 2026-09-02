import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Stock } from '../../../core/services/stock';

interface PulseStats {
  avgChangePct: number;
  advancing: number;
  declining: number;
  unchanged: number;
  totalMarketCap: number;
}

/**
 * "Pulse" = genuine aggregates over every tracked stock's real lastPrice vs
 * prevClose (same source data as Top Movers) — not a market index feed,
 * which BVMT doesn't expose here. The line graphic below it is a fixed
 * decorative flourish (same technique as the login hero), not a chart of
 * this data — it carries no axis or values, so it can't misread as one.
 */
@Component({
  selector: 'app-market-pulse',
  imports: [DecimalPipe],
  templateUrl: './market-pulse.html',
  styleUrl: './market-pulse.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketPulse {
  private readonly stockService = inject(Stock);

  protected readonly loading = signal(true);
  private readonly stats = signal<PulseStats | null>(null);

  protected readonly pulse = computed(() => this.stats());

  constructor() {
    this.stockService.filter({ page: 1, size: 100, sortField: 'symbol', sortDirection: 'ASC' }).subscribe({
      next: (res) => {
        let advancing = 0;
        let declining = 0;
        let unchanged = 0;
        let changeSum = 0;
        let changeCount = 0;
        let totalMarketCap = 0;

        for (const s of res.content) {
          if (s.marketCap !== null) {
            totalMarketCap += s.marketCap;
          }
          if (s.lastPrice === null || s.prevClose === null || s.prevClose === 0) {
            continue;
          }
          const pct = ((s.lastPrice - s.prevClose) / s.prevClose) * 100;
          changeSum += pct;
          changeCount++;
          if (pct > 0) advancing++;
          else if (pct < 0) declining++;
          else unchanged++;
        }

        this.stats.set({
          avgChangePct: changeCount > 0 ? changeSum / changeCount : 0,
          advancing,
          declining,
          unchanged,
          totalMarketCap,
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
