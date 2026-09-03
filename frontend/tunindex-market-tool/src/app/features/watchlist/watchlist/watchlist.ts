import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Stock } from '../../../core/services/stock';
import { Watchlist as WatchlistService } from '../../../core/services/watchlist';
import { SECTOR_LABELS, StockDto } from '../../../core/models/stock.model';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { RangeBar } from '../../../shared/components/range-bar/range-bar';
import { WatchlistStar } from '../../../shared/components/watchlist-star/watchlist-star';
import { TopOpportunities } from '../../dashboard/top-opportunities/top-opportunities';

@Component({
  selector: 'app-watchlist',
  imports: [EmptyState, SkeletonBlock, DecimalPipe, RangeBar, WatchlistStar, TopOpportunities],
  templateUrl: './watchlist.html',
  styleUrl: './watchlist.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Watchlist {
  private readonly stockService = inject(Stock);
  private readonly watchlistService = inject(WatchlistService);
  private readonly router = inject(Router);

  protected readonly sectorLabels = SECTOR_LABELS;
  protected readonly loading = signal(true);
  private readonly stocks = signal<StockDto[]>([]);

  protected readonly symbols = this.watchlistService.symbols;
  protected readonly rows = computed(() => {
    // Preserve watchlist order (most-recently-added last), not whatever order responses land in.
    const order = this.symbols();
    return order
      .map((symbol) => this.stocks().find((s) => s.symbol === symbol))
      .filter((s): s is StockDto => s !== undefined);
  });

  constructor() {
    effect(
      () => {
        const symbols = this.symbols();
        if (symbols.length === 0) {
          this.stocks.set([]);
          this.loading.set(false);
          return;
        }

        this.loading.set(true);
        forkJoin(symbols.map((symbol) => this.stockService.findBySymbol(symbol).pipe(catchError(() => of(null))))).subscribe(
          (results) => {
            this.stocks.set(results.filter((s): s is StockDto => s !== null));
            this.loading.set(false);
          },
        );
      },
      { allowSignalWrites: true },
    );
  }

  protected openStock(symbol: string): void {
    void this.router.navigate(['/app/stocks', symbol]);
  }

  protected dayChangePct(stock: StockDto): number | null {
    if (stock.lastPrice === null || stock.prevClose === null || stock.prevClose === 0) {
      return null;
    }
    return ((stock.lastPrice - stock.prevClose) / stock.prevClose) * 100;
  }
}
