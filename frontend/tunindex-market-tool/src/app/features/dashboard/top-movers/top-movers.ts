import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Stock } from '../../../core/services/stock';
import { StockDto } from '../../../core/models/stock.model';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';

export interface Mover {
  stock: StockDto;
  changePct: number;
}

const MOVERS_SHOWN = 5;

/**
 * "Gainers/losers" is computed client-side from lastPrice vs prevClose —
 * the backend has no dedicated ranking endpoint, but both fields are real
 * per-stock data already in every StockDto, so this is genuine day-change%,
 * not a synthetic figure.
 */
@Component({
  selector: 'app-top-movers',
  imports: [DecimalPipe, SkeletonBlock],
  templateUrl: './top-movers.html',
  styleUrl: './top-movers.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopMovers {
  private readonly stockService = inject(Stock);
  private readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  private readonly movers = signal<Mover[]>([]);

  protected readonly gainers = computed(() =>
    [...this.movers()].sort((a, b) => b.changePct - a.changePct).slice(0, MOVERS_SHOWN),
  );
  protected readonly losers = computed(() =>
    [...this.movers()].sort((a, b) => a.changePct - b.changePct).slice(0, MOVERS_SHOWN),
  );

  constructor() {
    this.stockService.filter({ page: 1, size: 100, sortField: 'symbol', sortDirection: 'ASC' }).subscribe({
      next: (res) => {
        const movers = res.content
          .filter((s) => s.lastPrice !== null && s.prevClose !== null && s.prevClose !== 0)
          .map((stock) => ({
            stock,
            changePct: ((stock.lastPrice! - stock.prevClose!) / stock.prevClose!) * 100,
          }));
        this.movers.set(movers);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  protected openStock(symbol: string): void {
    void this.router.navigate(['/app/stocks', symbol]);
  }
}
