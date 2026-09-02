import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OWNERSHIP_LABELS, SECTOR_LABELS, StockDto } from '../../../core/models/stock.model';
import { Stock } from '../../../core/services/stock';
import { Notification } from '../../../core/services/notification';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { StatTile } from '../../../shared/components/stat-tile/stat-tile';

@Component({
  selector: 'app-stock-detail',
  imports: [RouterLink, DecimalPipe, SkeletonBlock, StatTile],
  templateUrl: './stock-detail.html',
  styleUrl: './stock-detail.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockDetail {
  private readonly stockService = inject(Stock);
  private readonly notification = inject(Notification);
  private readonly route = inject(ActivatedRoute);

  protected readonly sectorLabels = SECTOR_LABELS;
  protected readonly ownershipLabels = OWNERSHIP_LABELS;

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly refreshing = signal(false);
  protected readonly stock = signal<StockDto | null>(null);

  private readonly paramMap = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  protected readonly symbol = computed(() => this.paramMap().get('symbol') ?? '');

  constructor() {
    this.load();
  }

  private load(): void {
    const symbol = this.symbol();
    if (!symbol) {
      this.error.set(true);
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    this.error.set(false);

    this.stockService.findBySymbol(symbol).subscribe({
      next: (res) => {
        this.stock.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  protected refresh(): void {
    const symbol = this.symbol();
    if (!symbol || this.refreshing()) {
      return;
    }
    this.refreshing.set(true);

    this.stockService.refresh(symbol).subscribe({
      next: () => {
        this.refreshing.set(false);
        this.notification.show('Refresh triggered', `${symbol} data will update shortly.`, 'success');
      },
      error: () => {
        this.refreshing.set(false);
        this.notification.show('Refresh failed', 'Could not trigger a data refresh.', 'error');
      },
    });
  }
}
