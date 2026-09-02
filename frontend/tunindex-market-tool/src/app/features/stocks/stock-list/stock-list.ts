import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { SECTOR_LABELS, SectorType, StockDto } from '../../../core/models/stock.model';
import { Stock, StockFilters } from '../../../core/services/stock';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { RangeBar } from '../../../shared/components/range-bar/range-bar';
import { WatchlistStar } from '../../../shared/components/watchlist-star/watchlist-star';

const PAGE_SIZE = 20;
const SECTOR_OPTIONS: SectorType[] = [
  'FINANCIALS',
  'BANKING',
  'TECHNOLOGY',
  'INDUSTRIALS',
  'CONSUMER_GOODS',
  'TELECOM',
  'ENERGY',
  'HEALTHCARE',
  'REAL_ESTATE',
  'UTILITIES',
  'OTHER',
];

type PresetKey = 'undervalued' | 'profitable' | 'grahamCriteria' | null;

@Component({
  selector: 'app-stock-list',
  imports: [Pagination, SkeletonBlock, DecimalPipe, RangeBar, WatchlistStar],
  templateUrl: './stock-list.html',
  styleUrl: './stock-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockList {
  private readonly stockService = inject(Stock);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly sectorOptions = SECTOR_OPTIONS;
  protected readonly sectorLabels = SECTOR_LABELS;

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly rows = signal<StockDto[]>([]);
  protected readonly page = signal(1);
  protected readonly totalPages = signal(1);
  protected readonly totalElements = signal(0);

  protected readonly searchInput = signal('');
  protected readonly sector = signal<SectorType | ''>('');
  protected readonly preset = signal<PresetKey>(null);

  private readonly initialQuery = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  constructor() {
    const q = this.initialQuery().get('q');
    if (q) {
      this.searchInput.set(q);
    }
    this.load();
  }

  protected onSearchSubmit(): void {
    this.page.set(1);
    this.load();
  }

  protected onSectorChange(value: string): void {
    this.sector.set(value as SectorType | '');
    this.page.set(1);
    this.load();
  }

  protected togglePreset(key: Exclude<PresetKey, null>): void {
    this.preset.set(this.preset() === key ? null : key);
    this.page.set(1);
    this.load();
  }

  protected onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(false);

    const filters: StockFilters = {};
    const search = this.searchInput().trim();
    if (search) {
      filters.name = search;
    }
    if (this.sector()) {
      filters.sector = this.sector();
    }
    if (this.preset()) {
      filters[this.preset() as Exclude<PresetKey, null>] = 'true';
    }

    this.stockService
      .filter({ page: this.page(), size: PAGE_SIZE, sortField: 'symbol', sortDirection: 'ASC', filters })
      .subscribe({
        next: (res) => {
          this.rows.set(res.content);
          this.totalPages.set(res.totalPages || 1);
          this.totalElements.set(res.totalElements);
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

  protected dayChangePct(stock: StockDto): number | null {
    if (stock.lastPrice === null || stock.prevClose === null || stock.prevClose === 0) {
      return null;
    }
    return ((stock.lastPrice - stock.prevClose) / stock.prevClose) * 100;
  }
}
