import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { PortfolioAnalytics as Analytics, PortfolioWeight } from '../../../core/models/portfolio-analytics.model';
import { Portfolio } from '../../../core/services/portfolio';

/**
 * What the portfolio is exposed to, as opposed to what it is worth.
 *
 * <p>The summary above it answers "am I up?". This answers the question a
 * position list cannot: whether the book is actually spread across anything,
 * how much of it rides on one name, and what income it throws off.
 *
 * <p>Every figure is computed server-side — including the observations, which
 * are written there so the same wording appears wherever analytics are shown.
 */
@Component({
  selector: 'app-portfolio-analytics',
  imports: [DecimalPipe],
  templateUrl: './portfolio-analytics.html',
  styleUrl: './portfolio-analytics.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioAnalyticsPanel {
  private readonly portfolio = inject(Portfolio);

  protected readonly analytics = signal<Analytics | null>(null);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly breakdown = signal<'sector' | 'position'>('sector');

  constructor() {
    this.portfolio.getAnalytics().subscribe({
      next: (data) => {
        this.analytics.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  protected readonly rows = computed<PortfolioWeight[]>(() => {
    const data = this.analytics();
    if (!data) {
      return [];
    }
    return this.breakdown() === 'sector' ? data.sectorWeights : data.positionWeights;
  });

  /**
   * Bars are scaled to the largest slice, not to 100%. With six holdings the
   * biggest is rarely over 40%, and scaling to 100 would render every bar as
   * a stub — the comparison between slices is what matters here.
   */
  protected readonly weightScale = computed(() => {
    const rows = this.rows();
    return rows.length === 0 ? 100 : Math.max(...rows.map((row) => row.weightPct), 1);
  });

  protected barWidth(row: PortfolioWeight): number {
    return (row.weightPct / this.weightScale()) * 100;
  }

  protected concentrationTone(label: string | null): string {
    switch (label) {
      case 'DIVERSIFIED':
        return 'good';
      case 'CONCENTRATED':
        return 'warn';
      default:
        return 'neutral';
    }
  }

  protected concentrationText(label: string | null): string {
    switch (label) {
      case 'DIVERSIFIED':
        return 'Diversified';
      case 'CONCENTRATED':
        return 'Concentrated';
      case 'MODERATE':
        return 'Moderate';
      default:
        return '—';
    }
  }

  protected setBreakdown(view: 'sector' | 'position'): void {
    this.breakdown.set(view);
  }

  /** Colour is assigned by position in the ordered list, so a slice keeps its
   *  hue when the user switches between the sector and position views only if
   *  its rank is unchanged — which is the honest signal, since rank is what
   *  the bars encode. */
  protected hueFor(index: number): string {
    const hues = [212, 168, 32, 268, 4, 190, 140, 320];
    return `hsl(${hues[index % hues.length]} 62% 52%)`;
  }
}
