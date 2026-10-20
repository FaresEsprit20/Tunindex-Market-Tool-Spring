import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { OpportunityScore, VERDICT_LABELS, Verdict } from '../../../core/models/opportunity.model';
import { Stock } from '../../../core/services/stock';
import { SkeletonBlock } from '../../../shared/components/skeleton-block/skeleton-block';

/**
 * The best-scoring buy candidates right now, as a dashboard summary. Same
 * Tunindex Score and same ranking as the Opportunities page — this is the
 * top of that list, with a way through to the rest of it.
 */
@Component({
  selector: 'app-top-opportunities',
  imports: [DecimalPipe, RouterLink, SkeletonBlock],
  templateUrl: './top-opportunities.html',
  styleUrl: './top-opportunities.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopOpportunities {
  private readonly stockService = inject(Stock);
  private readonly router = inject(Router);

  protected readonly verdictLabels = VERDICT_LABELS;

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly rows = signal<OpportunityScore[]>([]);

  constructor() {
    this.stockService.getOpportunities(5, 0).subscribe({
      next: (rows) => {
        this.rows.set(rows);
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

  protected scoreClass(score: number): string {
    if (score >= 80) return 'excellent';
    if (score >= 65) return 'good';
    if (score >= 50) return 'fair';
    return 'weak';
  }

  protected verdictClass(verdict: Verdict): string {
    switch (verdict) {
      case 'STRONG_BUY':
        return 'strong-buy';
      case 'BUY':
        return 'buy';
      case 'WATCH':
        return 'watch';
      default:
        return 'hold';
    }
  }
}
