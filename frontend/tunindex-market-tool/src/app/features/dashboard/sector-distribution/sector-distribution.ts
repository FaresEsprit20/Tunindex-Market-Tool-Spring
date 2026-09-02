import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { SECTOR_LABELS, SectorType } from '../../../core/models/stock.model';
import { Stock } from '../../../core/services/stock';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';

interface SectorRow {
  sector: SectorType;
  label: string;
  count: number;
  pct: number;
}

@Component({
  selector: 'app-sector-distribution',
  imports: [SkeletonBlock],
  templateUrl: './sector-distribution.html',
  styleUrl: './sector-distribution.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'panel-card' },
})
export class SectorDistribution {
  private readonly stockService = inject(Stock);

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  private readonly rawRows = signal<SectorRow[]>([]);

  protected readonly rows = computed(() => this.rawRows());
  protected readonly maxCount = computed(() => Math.max(1, ...this.rawRows().map((r) => r.count)));

  constructor() {
    this.stockService.countBySector().subscribe({
      next: (entries) => {
        const total = entries.reduce((sum, [, count]) => sum + count, 0) || 1;
        const rows: SectorRow[] = entries
          .map(([sector, count]) => ({
            sector,
            label: SECTOR_LABELS[sector] ?? sector,
            count,
            pct: (count / total) * 100,
          }))
          .sort((a, b) => b.count - a.count);
        this.rawRows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }
}
