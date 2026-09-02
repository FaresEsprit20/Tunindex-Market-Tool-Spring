import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { Stock } from '../../../core/services/stock';
import { StockDto } from '../../../core/models/stock.model';
import { TechnicalAnalysis, FundamentalAnalysis } from '../../../core/models/analysis.model';
import { RangeBar } from '../../../shared/components/range-bar/range-bar';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';

@Component({
  selector: 'app-analysis',
  imports: [DecimalPipe, RangeBar, SkeletonBlock, EmptyState],
  templateUrl: './analysis.html',
  styleUrl: './analysis.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Analysis {
  private readonly stockService = inject(Stock);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly paramMap = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });
  protected readonly symbol = computed(() => this.paramMap().get('symbol') ?? '');

  protected readonly searchInput = signal('');

  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly stock = signal<StockDto | null>(null);
  protected readonly technical = signal<TechnicalAnalysis | null>(null);
  protected readonly fundamental = signal<FundamentalAnalysis | null>(null);

  constructor() {
    this.load();
  }

  private load(): void {
    const symbol = this.symbol();
    if (!symbol) {
      return;
    }
    this.searchInput.set(symbol);
    this.loading.set(true);
    this.error.set(false);
    this.stock.set(null);
    this.technical.set(null);
    this.fundamental.set(null);

    this.stockService.findBySymbol(symbol).subscribe({
      next: (res) => this.stock.set(res),
      error: () => this.error.set(true),
    });

    this.stockService.getTechnicalAnalysis(symbol).subscribe({
      next: (res) => this.technical.set(res),
      error: () => {},
    });

    this.stockService.getFundamentalAnalysis(symbol).subscribe({
      next: (res) => {
        this.fundamental.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  protected onSearchSubmit(): void {
    const query = this.searchInput().trim().toUpperCase();
    if (!query) return;
    void this.router.navigate(['/app/analysis', query]);
  }

  protected scoreLabel(score: number): string {
    if (score >= 70) return 'Strong';
    if (score >= 45) return 'Moderate';
    return 'Weak';
  }

  protected ratingClass(rating: string): string {
    if (rating === 'STRONG') return 'positive';
    if (rating === 'WEAK') return 'negative';
    return 'warning';
  }

  protected trendClass(signal: string): string {
    if (signal === 'BULLISH') return 'positive';
    if (signal === 'BEARISH') return 'negative';
    return 'neutral';
  }
}
