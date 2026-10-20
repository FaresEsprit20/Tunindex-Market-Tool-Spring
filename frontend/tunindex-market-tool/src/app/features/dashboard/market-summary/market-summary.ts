import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { Stock } from '../../../core/services/stock';
import { StatTile } from '../../../shared/components/stat-tile/stat-tile';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';

interface SummaryCounts {
  total: number;
  undervalued: number;
  profitable: number;
  grahamMatches: number;
}

// Only totalElements from each response is used — page size 1 keeps the
// payload minimal since this is purely a count query.
const COUNT_ONLY_REQUEST = { page: 1, size: 1 };

@Component({
  selector: 'app-market-summary',
  imports: [StatTile, SkeletonBlock],
  templateUrl: './market-summary.html',
  styleUrl: './market-summary.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketSummary {
  private readonly stockService = inject(Stock);

  protected readonly loading = signal(true);
  protected readonly counts = signal<SummaryCounts | null>(null);

  constructor() {
    forkJoin({
      total: this.stockService.filter({ ...COUNT_ONLY_REQUEST }),
      undervalued: this.stockService.filter({ ...COUNT_ONLY_REQUEST, filters: { undervalued: 'true' } }),
      profitable: this.stockService.filter({ ...COUNT_ONLY_REQUEST, filters: { profitable: 'true' } }),
      grahamMatches: this.stockService.filter({ ...COUNT_ONLY_REQUEST, filters: { grahamCriteria: 'true' } }),
    }).subscribe({
      next: (res) => {
        this.counts.set({
          total: res.total.totalElements,
          undervalued: res.undervalued.totalElements,
          profitable: res.profitable.totalElements,
          grahamMatches: res.grahamMatches.totalElements,
        });
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
