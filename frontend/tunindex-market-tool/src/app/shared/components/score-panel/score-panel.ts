import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { OpportunityScore, SCORE_COMPONENTS, VERDICT_LABELS, Verdict } from '../../../core/models/opportunity.model';
import { Stock } from '../../../core/services/stock';
import { SkeletonBlock } from '../skeleton-block/skeleton-block';
import { ScoreRing } from '../score-ring/score-ring';

/**
 * One symbol's Tunindex Score with its component breakdown and the real
 * figures behind it. Shares the model and thresholds with the Opportunities
 * page so a stock reads identically in both places.
 */
@Component({
  selector: 'app-score-panel',
  imports: [SkeletonBlock, ScoreRing],
  templateUrl: './score-panel.html',
  styleUrl: './score-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScorePanel {
  readonly symbol = input.required<string>();

  private readonly stockService = inject(Stock);

  protected readonly verdictLabels = VERDICT_LABELS;
  protected readonly scoreComponents = SCORE_COMPONENTS;

  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly score = signal<OpportunityScore | null>(null);

  constructor() {
    effect(() => {
      const symbol = this.symbol();
      if (!symbol) return;

      this.loading.set(true);
      this.error.set(false);
      this.stockService.getScore(symbol).subscribe({
        next: (res) => {
          this.score.set(res);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.error.set(true);
        },
      });
    });
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
