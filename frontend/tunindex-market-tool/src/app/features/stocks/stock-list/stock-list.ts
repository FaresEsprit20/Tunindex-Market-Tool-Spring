import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { SECTOR_LABELS, SectorType, StockDto } from '../../../core/models/stock.model';
import { Stock, StockFilters } from '../../../core/services/stock';
import { Pagination } from '../../../shared/components/pagination/pagination';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { RangeBar } from '../../../shared/components/range-bar/range-bar';
import { WatchlistStar } from '../../../shared/components/watchlist-star/watchlist-star';
import { Sparkline } from '../../../shared/components/sparkline/sparkline';
import { exchangeCountry } from '../../../core/constants/exchange-flags';
import { PriceStream } from '../../../core/services/price-stream';
import { SplitPane } from '../../../shared/components/split-pane/split-pane';
import { StockPreview } from '../stock-preview/stock-preview';
import { CountryFlag } from '../../../shared/components/country-flag/country-flag';

// Rows are now ~24px, so a page shows a useful slice of the exchange
// instead of a fifth of it.
const PAGE_SIZE = 50;
const COLUMNS_STORAGE_KEY = 'tunindex-stock-columns';
const SECTOR_OPTIONS: SectorType[] = [
  'FINANCIALS',
  'BANKING',
  'INSURANCE',
  'TECHNOLOGY',
  'INDUSTRIALS',
  'MATERIALS',
  'CONSUMER_GOODS',
  'TELECOM',
  'ENERGY',
  'HEALTHCARE',
  'REAL_ESTATE',
  'UTILITIES',
  'OTHER',
];

/**
 * Investor presets are mutually exclusive by design: each is a complete,
 * named strategy (a bundle of thresholds), and stacking two of them just
 * intersects into noise.
 */
type PresetKey =
  | 'grahamCriteria'
  | 'valueInvestorFavorites'
  | 'growthInvestorFavorites'
  | 'incomeInvestorFavorites'
  | 'contrarianFavorites';

const PRESET_LABELS: Record<PresetKey, string> = {
  grahamCriteria: 'Graham criteria',
  valueInvestorFavorites: 'Value investor',
  growthInvestorFavorites: 'Growth investor',
  incomeInvestorFavorites: 'Income investor',
  contrarianFavorites: 'Contrarian',
};
const PRESET_KEYS: PresetKey[] = [
  'grahamCriteria',
  'valueInvestorFavorites',
  'growthInvestorFavorites',
  'incomeInvestorFavorites',
  'contrarianFavorites',
];

/**
 * Single-condition flags. Unlike presets these combine freely — the
 * backend ANDs every filter it is given — so they are multi-select, except
 * within the contradictory pairs below.
 */
type FlagKey =
  | 'profitable'
  | 'undervalued'
  | 'overvalued'
  | 'lowDebt'
  | 'highDebt'
  | 'lowPeRatio'
  | 'highDividend'
  | 'priceBelowGrahamValue'
  | 'priceAboveGrahamValue'
  | 'near52WeekLow'
  | 'near52WeekHigh';

const FLAG_LABELS: Record<FlagKey, string> = {
  profitable: 'Profitable',
  undervalued: 'Undervalued',
  overvalued: 'Overvalued',
  lowDebt: 'Low debt',
  highDebt: 'High debt',
  lowPeRatio: 'Low P/E',
  highDividend: 'High dividend',
  priceBelowGrahamValue: 'Below Graham value',
  priceAboveGrahamValue: 'Above Graham value',
  near52WeekLow: 'Near 52W low',
  near52WeekHigh: 'Near 52W high',
};

/** The three chips shown inline; the rest live behind "More filters". */
const PRIMARY_FLAGS: FlagKey[] = ['undervalued', 'profitable', 'near52WeekLow'];
const MORE_FLAGS: FlagKey[] = [
  'overvalued',
  'lowDebt',
  'highDebt',
  'lowPeRatio',
  'highDividend',
  'priceBelowGrahamValue',
  'priceAboveGrahamValue',
  'near52WeekHigh',
];

/**
 * Pairs that cannot both hold. Selecting one clears the other rather than
 * letting the user build a query that must return zero rows.
 */
const OPPOSITE_FLAGS: Partial<Record<FlagKey, FlagKey>> = {
  undervalued: 'overvalued',
  overvalued: 'undervalued',
  lowDebt: 'highDebt',
  highDebt: 'lowDebt',
  priceBelowGrahamValue: 'priceAboveGrahamValue',
  priceAboveGrahamValue: 'priceBelowGrahamValue',
  near52WeekLow: 'near52WeekHigh',
  near52WeekHigh: 'near52WeekLow',
};

const OWNERSHIP_OPTIONS = [
  { value: 'PRIVATE', label: 'Private sector' },
  { value: 'GOVERNMENT', label: 'Government owned' },
];

interface RangeFilter {
  min: string;
  max: string;
}

/**
 * Only fields the backend can actually order by — each one is a case in
 * StockServiceImpl.mapSortField, which resolves it to the real embedded
 * entity path (e.g. lastPrice -> priceData.lastPrice). "Change" is absent
 * on purpose: it's derived client-side from lastPrice vs prevClose and has
 * no column to sort on.
 */
type SortField = 'symbol' | 'name' | 'sector' | 'lastPrice' | 'closeTo52weekslowPct' | 'peRatio' | 'marginOfSafety';

interface TableColumn {
  /** null = not sortable server-side; the header renders as plain text. */
  field: SortField | null;
  label: string;
  numeric: boolean;
}

/**
 * Declared in the exact left-to-right order the body cells are rendered in
 * (stock-list.html) — the header row is generated from this list, so the
 * two stay aligned by construction.
 */
const TABLE_COLUMNS: TableColumn[] = [
  { field: 'symbol', label: 'Symbol', numeric: false },
  { field: 'name', label: 'Name', numeric: false },
  { field: 'sector', label: 'Sector', numeric: false },
  { field: 'lastPrice', label: 'Price', numeric: true },
  { field: null, label: 'Change', numeric: true },
  // Drawn from stored closes, so there is no column to order it by.
  { field: null, label: '30D trend', numeric: false },
  { field: 'closeTo52weekslowPct', label: '52W range', numeric: false },
  { field: 'peRatio', label: 'P/E', numeric: true },
  { field: 'marginOfSafety', label: 'Margin of safety', numeric: true },
];

@Component({
  selector: 'app-stock-list',
  imports: [Pagination, SkeletonBlock, DecimalPipe, RangeBar, WatchlistStar, Sparkline, CountryFlag, SplitPane, StockPreview],
  templateUrl: './stock-list.html',
  styleUrl: './stock-list.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockList {
  protected readonly exchangeCountry = exchangeCountry;
  private readonly stockService = inject(Stock);
  protected readonly priceStream = inject(PriceStream);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly sectorOptions = SECTOR_OPTIONS;
  protected readonly sectorLabels = SECTOR_LABELS;
  protected readonly presetLabels = PRESET_LABELS;
  protected readonly presetKeys = PRESET_KEYS;
  protected readonly flagLabels = FLAG_LABELS;
  protected readonly primaryFlags = PRIMARY_FLAGS;
  protected readonly moreFlags = MORE_FLAGS;
  protected readonly ownershipOptions = OWNERSHIP_OPTIONS;

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly rows = signal<StockDto[]>([]);
  /** symbol -> closing prices, for the row sparklines. */
  protected readonly sparklines = signal<Record<string, number[]>>({});
  protected readonly page = signal(1);
  protected readonly totalPages = signal(1);
  protected readonly totalElements = signal(0);

  protected readonly tableColumns = TABLE_COLUMNS;

  /**
   * Which columns are shown. Symbol is deliberately absent from the
   * hideable set — a grid with no identifier column is unusable, so it is
   * never offered as something to turn off.
   */
  protected readonly hideableColumns = TABLE_COLUMNS.filter((c) => c.label !== 'Symbol');
  protected readonly visibleColumns = signal<ReadonlySet<string>>(this.readColumnPrefs());
  protected readonly columnMenuOpen = signal(false);

  /** Row the keyboard is on, as an index into rows(). -1 = none. */
  protected readonly activeRow = signal(-1);

  /**
   * Symbol shown in the preview pane. Selection, not navigation: a single
   * click keeps you in the list, which is the whole point of the split —
   * comparing two stocks used to cost a round trip through the detail page.
   * Enter and the Open button still navigate.
   */
  protected readonly selectedSymbol = signal<string | null>(null);
  protected readonly sortField = signal<SortField>('symbol');
  protected readonly sortDirection = signal<'ASC' | 'DESC'>('ASC');

  protected readonly searchInput = signal('');
  protected readonly sector = signal<SectorType | ''>('');
  protected readonly ownershipType = signal('');
  protected readonly preset = signal<PresetKey | null>(null);
  protected readonly flags = signal<ReadonlySet<FlagKey>>(new Set());
  protected readonly showAdvanced = signal(false);

  protected readonly priceRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly peRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly dividendYieldRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly debtToEquityRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly marginOfSafetyRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly profitMarginRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly epsRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly bvpsRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly grahamFairValueRange = signal<RangeFilter>({ min: '', max: '' });
  protected readonly closeTo52WeekLowRange = signal<RangeFilter>({ min: '', max: '' });

  /** Every range signal, so clear/count logic never misses a newly-added one. */
  private allRanges(): RangeFilter[] {
    return [
      this.priceRange(),
      this.peRange(),
      this.dividendYieldRange(),
      this.debtToEquityRange(),
      this.marginOfSafetyRange(),
      this.profitMarginRange(),
      this.epsRange(),
      this.bvpsRange(),
      this.grahamFairValueRange(),
      this.closeTo52WeekLowRange(),
    ];
  }

  private readonly queryParam = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });

  constructor() {
    // One shared stream for the app; connecting twice is a no-op.
    this.priceStream.connect();

    // Angular reuses this component instance for repeat navigations to
    // /app/stocks (e.g. a navbar search while already on this page), so a
    // constructor-only fetch would leave stale results on screen. Track
    // queryParam() so a new external ?q= reloads too, not just first mount.
    let firstRun = true;
    effect(() => {
      const params = this.queryParam();
      const q = params.get('q') ?? '';
      if (firstRun) {
        firstRun = false;
        if (q) {
          this.searchInput.set(q);
        }
        // Filter flags can arrive in the URL — from a clicked scorer reason,
        // or a shared link. Applying them here is also what makes a screen
        // bookmarkable.
        const incoming = new Set<FlagKey>();
        for (const key of [...PRIMARY_FLAGS, ...MORE_FLAGS]) {
          if (params.get(key) === 'true') {
            incoming.add(key);
          }
        }
        if (incoming.size > 0) {
          this.flags.set(incoming);
          this.showAdvanced.set(true);
        }
        this.load();
        return;
      }
      if (q !== this.searchInput()) {
        this.searchInput.set(q);
        this.page.set(1);
        this.load();
      }
    });
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

  protected onOwnershipChange(value: string): void {
    this.ownershipType.set(value);
    this.page.set(1);
    this.load();
  }

  protected togglePreset(key: PresetKey): void {
    this.preset.set(this.preset() === key ? null : key);
    this.page.set(1);
    this.load();
  }

  protected toggleFlag(key: FlagKey): void {
    const next = new Set(this.flags());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
      // Turning on one half of a contradictory pair turns the other off,
      // rather than letting the two AND into a guaranteed-empty result.
      const opposite = OPPOSITE_FLAGS[key];
      if (opposite) {
        next.delete(opposite);
      }
    }
    this.flags.set(next);
    this.page.set(1);
    this.load();
  }

  protected isFlagActive(key: FlagKey): boolean {
    return this.flags().has(key);
  }

  protected toggleAdvanced(): void {
    this.showAdvanced.update((v) => !v);
  }

  protected applyAdvancedFilters(): void {
    this.page.set(1);
    this.load();
  }

  protected clearAdvancedFilters(): void {
    const empty = { min: '', max: '' };
    this.priceRange.set(empty);
    this.peRange.set(empty);
    this.dividendYieldRange.set(empty);
    this.debtToEquityRange.set(empty);
    this.marginOfSafetyRange.set(empty);
    this.profitMarginRange.set(empty);
    this.epsRange.set(empty);
    this.bvpsRange.set(empty);
    this.grahamFairValueRange.set(empty);
    this.closeTo52WeekLowRange.set(empty);
    this.flags.set(new Set());
    this.preset.set(null);
    this.ownershipType.set('');
    this.page.set(1);
    this.load();
  }

  protected onPageChange(page: number): void {
    this.page.set(page);
    this.load();
  }

  /**
   * Clicking the active column flips its direction; clicking a new one
   * sorts it ascending first. Sorting is server-side over the whole result
   * set, not just the current page, so it resets back to page 1.
   */
  protected toggleSort(field: SortField): void {
    if (this.sortField() === field) {
      this.sortDirection.update((direction) => (direction === 'ASC' ? 'DESC' : 'ASC'));
    } else {
      this.sortField.set(field);
      this.sortDirection.set('ASC');
    }
    this.page.set(1);
    this.load();
  }

  protected activeAdvancedCount(): number {
    const ranges = this.allRanges().filter((r) => r.min.trim() || r.max.trim()).length;
    const flagsInPanel = this.moreFlags.filter((key) => this.flags().has(key)).length;
    return ranges + flagsInPanel + (this.preset() ? 1 : 0) + (this.ownershipType() ? 1 : 0);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(false);

    const filters: StockFilters = {};
    const search = this.searchInput().trim();
    if (search) {
      // One field, matched against ticker OR company name server-side.
      // This used to guess: a query with no space was searched as a symbol,
      // which meant every one-word company name ("Amen", "Carthage",
      // "Delice") matched nothing at all.
      filters.search = search;
    }
    if (this.sector()) {
      filters.sector = this.sector();
    }
    if (this.ownershipType()) {
      filters.ownershipType = this.ownershipType();
    }
    if (this.preset()) {
      filters[this.preset()!] = 'true';
    }
    // Flags all AND together server-side, so every selected one is sent.
    for (const flag of this.flags()) {
      filters[flag] = 'true';
    }

    this.applyRange(filters, this.priceRange(), 'minPrice', 'maxPrice');
    this.applyRange(filters, this.peRange(), 'minPeRatio', 'maxPeRatio');
    this.applyRange(filters, this.dividendYieldRange(), 'minDividendYield', 'maxDividendYield');
    this.applyRange(filters, this.debtToEquityRange(), 'minDebtToEquity', 'maxDebtToEquity');
    this.applyRange(filters, this.marginOfSafetyRange(), 'minMarginOfSafety', 'maxMarginOfSafety');
    this.applyRange(filters, this.profitMarginRange(), 'minProfitMargin', 'maxProfitMargin');
    this.applyRange(filters, this.epsRange(), 'minEps', 'maxEps');
    this.applyRange(filters, this.bvpsRange(), 'minBvps', 'maxBvps');
    this.applyRange(filters, this.grahamFairValueRange(), 'minGrahamFairValue', 'maxGrahamFairValue');
    this.applyRange(filters, this.closeTo52WeekLowRange(), 'minCloseTo52WeekLow', 'maxCloseTo52WeekLow');

    this.stockService
      .filter({
        page: this.page(),
        size: PAGE_SIZE,
        sortField: this.sortField(),
        sortDirection: this.sortDirection(),
        filters,
      })
      .subscribe({
        next: (res) => {
          this.rows.set(res.content);
          this.totalPages.set(res.totalPages || 1);
          this.totalElements.set(res.totalElements);
          this.loading.set(false);
          this.loadSparklines(res.content.map((s) => s.symbol));
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

  /**
   * One batch request for the whole page's traces. Failures are swallowed:
   * a missing sparkline should leave the cell blank, never block the table.
   */
  private loadSparklines(symbols: string[]): void {
    if (symbols.length === 0) {
      this.sparklines.set({});
      return;
    }
    this.stockService.getSparklines(symbols, 30).subscribe({
      next: (series) => this.sparklines.set(series),
      error: () => this.sparklines.set({}),
    });
  }

  protected isColumnVisible(label: string): boolean {
    return label === 'Symbol' || this.visibleColumns().has(label);
  }

  protected toggleColumn(label: string): void {
    const next = new Set(this.visibleColumns());
    next.has(label) ? next.delete(label) : next.add(label);
    this.visibleColumns.set(next);
    try {
      localStorage.setItem(COLUMNS_STORAGE_KEY, JSON.stringify([...next]));
    } catch {
      // Private browsing — the choice just won't survive a reload.
    }
  }

  /** Count of columns currently rendered, so colspans stay correct. */
  protected visibleColumnCount(): number {
    // +1 for the always-present star column.
    return 1 + TABLE_COLUMNS.filter((c) => this.isColumnVisible(c.label)).length;
  }

  private readColumnPrefs(): ReadonlySet<string> {
    const all = new Set(TABLE_COLUMNS.map((c) => c.label));
    try {
      const stored = localStorage.getItem(COLUMNS_STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored) as string[];
        // Intersect with the current column set so a stored name that no
        // longer exists can't resurrect a column or hide a new one.
        return new Set(parsed.filter((label) => all.has(label)));
      }
    } catch {
      // Fall through to "everything visible".
    }
    return all;
  }

  /** ↑/↓ move the focused row, Enter opens it — the grid without a mouse. */
  protected onGridKeydown(event: KeyboardEvent): void {
    const count = this.rows().length;
    if (count === 0) return;

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.activeRow.set(Math.min(this.activeRow() + 1, count - 1));
        this.syncSelectionToActiveRow();
        this.scrollActiveIntoView();
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.activeRow.set(Math.max(this.activeRow() - 1, 0));
        this.syncSelectionToActiveRow();
        this.scrollActiveIntoView();
        break;
      case 'Home':
        event.preventDefault();
        this.activeRow.set(0);
        this.syncSelectionToActiveRow();
        this.scrollActiveIntoView();
        break;
      case 'End':
        event.preventDefault();
        this.activeRow.set(count - 1);
        this.syncSelectionToActiveRow();
        this.scrollActiveIntoView();
        break;
      case 'Enter': {
        const row = this.rows()[this.activeRow()];
        if (row) {
          event.preventDefault();
          this.openStock(row.symbol);
        }
        break;
      }
    }
  }

  /** Keeps the preview in step with keyboard movement through the grid. */
  private syncSelectionToActiveRow(): void {
    const row = this.rows()[this.activeRow()];
    if (row) {
      this.selectedSymbol.set(row.symbol);
    }
  }

  private scrollActiveIntoView(): void {
    queueMicrotask(() => {
      document.querySelector('.stock-row.active')?.scrollIntoView({ block: 'nearest' });
    });
  }

  /** Pushed price if one has arrived, otherwise the value from the fetch. */
  protected livePrice(stock: StockDto): number | null {
    return this.priceStream.prices()[stock.symbol]?.price ?? stock.lastPrice;
  }

  protected liveChangePct(stock: StockDto): number | null {
    const pushed = this.priceStream.prices()[stock.symbol];
    if (pushed?.changePct !== undefined && pushed.changePct !== null) {
      return pushed.changePct;
    }
    return this.dayChangePct(stock);
  }

  protected flashFor(symbol: string): 'up' | 'down' | null {
    return this.priceStream.flashes()[symbol] ?? null;
  }

  protected sparklineFor(symbol: string): number[] {
    return this.sparklines()[symbol] ?? [];
  }

  /**
   * The symbol currently morphing into the detail view. Only one element on
   * the page may carry a given view-transition-name at a time, so this is a
   * single value rather than a set — naming every row would make the
   * transition ambiguous and the browser would skip it entirely.
   */
  protected readonly transitioningSymbol = signal<string | null>(null);

  /** Single click / arrow keys: preview it without leaving the grid. */
  protected selectStock(symbol: string, index: number): void {
    this.selectedSymbol.set(symbol);
    this.activeRow.set(index);
  }

  /** Enter, double-click, or the preview's own link: go to the full page. */
  protected openStock(symbol: string): void {
    this.transitioningSymbol.set(symbol);
    void this.router.navigate(['/app/stocks', symbol]);
  }

  protected dayChangePct(stock: StockDto): number | null {
    if (stock.lastPrice === null || stock.prevClose === null || stock.prevClose === 0) {
      return null;
    }
    return ((stock.lastPrice - stock.prevClose) / stock.prevClose) * 100;
  }
}
