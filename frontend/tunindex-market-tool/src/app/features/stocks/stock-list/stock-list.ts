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

// Every key here maps 1:1 to a boolean filter the backend already supports
// (StockServiceImpl.buildSpecificationFromFilters) — this UI previously
// only exposed 3 of these.
type PresetKey =
  | 'undervalued'
  | 'profitable'
  | 'grahamCriteria'
  | 'lowDebt'
  | 'highDividend'
  | 'lowPeRatio'
  | 'valueInvestorFavorites'
  | 'growthInvestorFavorites'
  | 'incomeInvestorFavorites'
  | 'contrarianFavorites';

const PRESET_LABELS: Record<PresetKey, string> = {
  undervalued: 'Undervalued',
  profitable: 'Profitable',
  grahamCriteria: 'Graham criteria',
  lowDebt: 'Low debt',
  highDividend: 'High dividend',
  lowPeRatio: 'Low P/E',
  valueInvestorFavorites: 'Value investor',
  growthInvestorFavorites: 'Growth investor',
  incomeInvestorFavorites: 'Income investor',
  contrarianFavorites: 'Contrarian',
};
const PRIMARY_PRESETS: PresetKey[] = ['undervalued', 'profitable', 'grahamCriteria'];
const MORE_PRESETS: PresetKey[] = [
  'lowDebt',
  'highDividend',
  'lowPeRatio',
  'valueInvestorFavorites',
  'growthInvestorFavorites',
  'incomeInvestorFavorites',
  'contrarianFavorites',
];

interface RangeFilter {
  min: string;
  max: string;
}

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
  protected readonly presetLabels = PRESET_LABELS;
  protected readonly primaryPresets = PRIMARY_PRESETS;
  protected readonly morePresets = MORE_PRESETS;

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly rows = signal<StockDto[]>([]);
  protected readonly page = signal(1);
  protected readonly totalPages = signal(1);
  protected readonly totalElements = signal(0);

  protected readonly searchInput = signal('');
  protected readonly sector = signal<SectorType | ''>('');
  protected readonly preset = signal<PresetKey | null>(null);
  protected readonly showAdvanced = signal(false);

  protected readonly priceRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly peRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly dividendYieldRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly debtToEquityRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly marginOfSafetyRange = signal<RangeFilter>({ min: '', max: '' });

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

  protected togglePreset(key: PresetKey): void {
    this.preset.set(this.preset() === key ? null : key);
    this.page.set(1);
    this.load();
  }

  protected toggleAdvanced(): void {
    this.showAdvanced.update((v) => !v);
  }

  protected applyAdvancedFilters(): void {
    this.page.set(1);
    this.load();
  }

  protected clearAdvancedFilters(): void {
    this.priceRange.set({ min: '', max: '' });
    this.peRange.set({ min: '', max: '' });
    this.dividendYieldRange.set({ min: '', max: '' });
    this.debtToEquityRange.set({ min: '', max: '' });
    this.marginOfSafetyRange.set({ min: '', max: '' });
    this.page.set(1);
    this.load();
  }

  protected onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected activeAdvancedCount(): number {
    return [this.priceRange(), this.peRange(), this.dividendYieldRange(), this.debtToEquityRange(), this.marginOfSafetyRange()]
      .filter((r) => r.min.trim() || r.max.trim()).length;
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
      filters[this.preset()!] = 'true';
    }

    this.applyRange(filters, this.priceRange(), 'minPrice', 'maxPrice');
    this.applyRange(filters, this.peRange(), 'minPeRatio', 'maxPeRatio');
    this.applyRange(filters, this.dividendYieldRange(), 'minDividendYield', 'maxDividendYield');
    this.applyRange(filters, this.debtToEquityRange(), 'minDebtToEquity', 'maxDebtToEquity');
    this.applyRange(filters, this.marginOfSafetyRange(), 'minMarginOfSafety', 'maxMarginOfSafety');

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

  private applyRange(filters: StockFilters, range: RangeFilter, minKey: keyof StockFilters, maxKey: keyof StockFilters): void {
    if (range.min.trim()) {
      (filters as Record<string, string>)[minKey] = range.min.trim();
    }
    if (range.max.trim()) {
      (filters as Record<string, string>)[maxKey] = range.max.trim();
    }
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
