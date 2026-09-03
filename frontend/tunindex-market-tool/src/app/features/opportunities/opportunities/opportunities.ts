import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OpportunityScore, SCORE_COMPONENTS, VERDICT_LABELS, Verdict } from '../../../core/models/opportunity.model';
import { SECTOR_LABELS, SectorType } from '../../../core/models/stock.model';
import { Stock } from '../../../core/services/stock';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';
import { EmptyState } from '../../../shared/components/empty-state/empty-state';
import { WatchlistStar } from '../../../shared/components/watchlist-star/watchlist-star';

const MIN_SCORE_OPTIONS = [0, 50, 65, 80];

/**
 * The opportunity hunter: every tracked stock scored by the Tunindex
 * Scorer and ranked best-first, with the real reasons behind each score.
 * Scoring happens entirely server-side (see TunindexScorer on the
 * collector) — this page only ranks and explains what it returns.
 */
@Component({
  selector: 'app-opportunities',
  imports: [DecimalPipe, SkeletonBlock, EmptyState, WatchlistStar],
  templateUrl: './opportunities.html',
  styleUrl: './opportunities.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Opportunities {
  private readonly stockService = inject(Stock);
  private readonly router = inject(Router);

  protected readonly sectorLabels = SECTOR_LABELS;
  protected readonly verdictLabels = VERDICT_LABELS;
  protected readonly scoreComponents = SCORE_COMPONENTS;
  protected readonly minScoreOptions = MIN_SCORE_OPTIONS;

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly rows = signal<OpportunityScore[]>([]);
  protected readonly minScore = signal(0);
  protected readonly expanded = signal<string | null>(null);

  protected readonly strongBuyCount = computed(
    () => this.rows().filter((row) => row.verdict === 'STRONG_BUY').length,
  );
  protected readonly buyCount = computed(() => this.rows().filter((row) => row.verdict === 'BUY').length);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.stockService.getOpportunities(30, this.minScore()).subscribe({
      next: (res) => {
        this.rows.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }

  protected onMinScoreChange(value: string): void {
    this.minScore.set(Number(value));
    this.load();
  }

  protected toggleExpanded(symbol: string): void {
    this.expanded.update((current) => (current === symbol ? null : symbol));
  }

  protected openStock(symbol: string): void {
    void this.router.navigate(['/app/stocks', symbol]);
  }

  protected sectorLabel(sector: string | null): string {
    if (!sector) return '—';
    return this.sectorLabels[sector as SectorType] ?? sector;
  }

  protected verdictClass(verdict: Verdict): string {
    switch (verdict) {
      case 'STRONG_BUY':
        return 'strong-buy';
      case 'BUY':
        return 'buy';
      case 'WATCH':
        return 'watch';
      case 'HOLD':
        return 'hold';
      default:
        return 'avoid';
    }
  }

  /** Colour band for the score ring — matches the verdict thresholds. */
  protected scoreClass(score: number): string {
    if (score >= 80) return 'excellent';
    if (score >= 65) return 'good';
    if (score >= 50) return 'fair';
    return 'weak';
  }

  protected componentValue(row: OpportunityScore, key: string): number | null {
    return row[key as keyof OpportunityScore] as number | null;
  }
}
