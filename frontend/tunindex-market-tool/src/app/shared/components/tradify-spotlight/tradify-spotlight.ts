import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Stock } from '../../../core/services/stock';
import { Market } from '../../../core/services/market';
import { OpportunityScore } from '../../../core/models/opportunity.model';

/**
 * The dashboard's showcase for the Tradify Scorer and Analyst.
 *
 * <p>Deliberately not a marketing panel. A band that claims the tools are
 * powerful is worth nothing on a page full of real figures - and on a finance
 * screen it actively costs trust. So this shows the work instead: the best
 * entry the analyst is holding <em>right now</em>, with the live levels
 * attached, and the count of companies it went through to find it.
 *
 * <p>That also means it degrades honestly. When nothing qualifies it says so
 * rather than dressing up a weak candidate, because a spotlight that always
 * has something exciting to report is one nobody believes twice.
 */
@Component({
  selector: 'app-tradify-spotlight',
  imports: [DecimalPipe, RouterLink],
  templateUrl: './tradify-spotlight.html',
  styleUrl: './tradify-spotlight.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TradifySpotlight {
  private readonly stock = inject(Stock);
  private readonly market = inject(Market);

  protected readonly loading = signal(true);
  protected readonly rows = signal<OpportunityScore[]>([]);
  protected readonly universe = signal<number | null>(null);

  /** Drives the one-time reveal once real content is in place. */
  protected readonly revealed = signal(false);

  constructor() {
    forkJoin({
      opportunities: this.stock.getOpportunities(20, 0).pipe(catchError(() => of([]))),
      breadth: this.market.getBreadth().pipe(catchError(() => of(null))),
    }).subscribe(({ opportunities, breadth }) => {
      this.rows.set(opportunities);
      this.universe.set(breadth?.total ?? null);
      this.loading.set(false);
      // Next frame, so the transition has a "before" to animate from rather
      // than painting the finished state immediately.
      requestAnimationFrame(() => this.revealed.set(true));
    });
  }

  /**
   * The one to lead with.
   *
   * <p>A live entry outranks a high score: the page is trying to show that the
   * analyst finds a moment, not that it can rank a list. Among live entries,
   * the biggest upside to the first target wins; failing that, the best score.
   */
  protected readonly find = computed<OpportunityScore | null>(() => {
    const all = this.rows().filter((row) => row.tradeSetup);
    if (all.length === 0) {
      return null;
    }
    const live = all.filter((row) => row.tradeSetup!.stance === 'ACCUMULATE_NOW');
    const pool = live.length > 0 ? live : [];
    if (pool.length === 0) {
      return null;
    }
    return pool.reduce((best, row) =>
      (row.tradeSetup!.expectedReturnPct ?? 0) > (best.tradeSetup!.expectedReturnPct ?? 0) ? row : best,
    );
  });

  /** Shown when nothing is live: still true, still useful. */
  protected readonly nextBest = computed<OpportunityScore | null>(() => {
    const waiting = this.rows().filter((row) => row.tradeSetup?.stance === 'BUY_THE_DIP');
    if (waiting.length === 0) {
      return null;
    }
    return waiting.reduce((best, row) =>
      (row.tradeSetup!.distanceToZonePct ?? 99) < (best.tradeSetup!.distanceToZonePct ?? 99) ? row : best,
    );
  });

  protected readonly liveCount = computed(
    () => this.rows().filter((row) => row.tradeSetup?.stance === 'ACCUMULATE_NOW').length,
  );

  protected readonly watchCount = computed(
    () =>
      this.rows().filter((row) => {
        const stance = row.tradeSetup?.stance;
        return stance === 'BUY_THE_DIP' || stance === 'WAIT_FOR_CONFIRMATION';
      }).length,
  );
}
