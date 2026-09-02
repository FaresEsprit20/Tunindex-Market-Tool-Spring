import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { OWNERSHIP_LABELS, OwnershipType } from '../../../core/models/stock.model';
import { Stock } from '../../../core/services/stock';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';

interface OwnershipRow {
  ownership: OwnershipType;
  label: string;
  count: number;
  pct: number;
}

@Component({
  selector: 'app-ownership-distribution',
  imports: [SkeletonBlock, DecimalPipe],
  templateUrl: './ownership-distribution.html',
  styleUrl: './ownership-distribution.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'panel-card' },
})
export class OwnershipDistribution {
  private readonly stockService = inject(Stock);

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly rows = signal<OwnershipRow[]>([]);
  protected readonly total = computed(() => this.rows().reduce((sum, r) => sum + r.count, 0));

  constructor() {
    this.stockService.countByOwnership().subscribe({
      next: (entries) => {
        const total = entries.reduce((sum, [, count]) => sum + count, 0) || 1;
        const rows: OwnershipRow[] = entries
          .map(([ownership, count]) => ({
            ownership,
            label: OWNERSHIP_LABELS[ownership] ?? ownership,
            count,
            pct: (count / total) * 100,
          }))
          .sort((a, b) => b.count - a.count);
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }
}
