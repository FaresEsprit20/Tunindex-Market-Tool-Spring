import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OpportunityScore, SCORE_COMPONENTS } from '../../../core/models/opportunity.model';

const SIZE = 108;
const STROKE = 9;
/** Gap between arcs, in degrees, so the segments read as separate. */
const GAP_DEG = 3;

interface Arc {
  key: string;
  label: string;
  weight: number;
  score: number | null;
  /** Faint full-length arc showing the slot this component occupies. */
  trackDash: string;
  trackOffset: number;
  /** Filled portion, proportional to the component's score. */
  fillDash: string;
  fillOffset: number;
  colorClass: string;
}

/**
 * The Tunindex Score as a segmented ring: each of the six weighted
 * components is an arc whose <em>length</em> is its weight and whose
 * <em>fill</em> is its score.
 *
 * <p>The previous ring was a number inside a circle — the circle carried no
 * information. This version makes the ring the breakdown: a weak valuation
 * inside a strong overall score is visible without expanding anything, in
 * the same footprint.
 */
@Component({
  selector: 'app-score-ring',
  imports: [],
  templateUrl: './score-ring.html',
  styleUrl: './score-ring.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScoreRing {
  readonly score = input.required<OpportunityScore>();

  protected readonly size = SIZE;
  protected readonly center = SIZE / 2;
  protected readonly radius = (SIZE - STROKE) / 2;
  protected readonly strokeWidth = STROKE;

  private get circumference(): number {
    return 2 * Math.PI * this.radius;
  }

  protected readonly arcs = computed<Arc[]>(() => {
    const row = this.score();
    const c = this.circumference;
    // Convert the degree gap into path length once, rather than per arc.
    const gapLen = (GAP_DEG / 360) * c;

    let cursorWeight = 0;
    return SCORE_COMPONENTS.map((component) => {
      const value = row[component.key as keyof OpportunityScore] as number | null;
      const slotLen = (component.weight / 100) * c;
      const drawLen = Math.max(0, slotLen - gapLen);
      const startLen = (cursorWeight / 100) * c;
      cursorWeight += component.weight;

      const fillLen = value === null ? 0 : drawLen * (value / 100);

      return {
        key: component.key,
        label: component.label,
        weight: component.weight,
        score: value,
        trackDash: `${drawLen} ${c - drawLen}`,
        // Negative offset walks the dash pattern forward along the path.
        trackOffset: -startLen,
        fillDash: `${fillLen} ${c - fillLen}`,
        fillOffset: -startLen,
        colorClass: this.bandFor(value),
      };
    });
  });

  protected readonly overallClass = computed(() => this.bandFor(this.score().overallScore));

  private bandFor(value: number | null): string {
    if (value === null) return 'none';
    if (value >= 80) return 'excellent';
    if (value >= 65) return 'good';
    if (value >= 50) return 'fair';
    return 'weak';
  }

  /** Tooltip text for an arc — the component, its weight and its score. */
  protected arcTitle(arc: Arc): string {
    const score = arc.score === null ? 'no data' : `${arc.score}/100`;
    return `${arc.label} · ${arc.weight}% of the score · ${score}`;
  }
}
