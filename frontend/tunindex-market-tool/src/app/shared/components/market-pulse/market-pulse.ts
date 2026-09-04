import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  MarketBreadth,
  MarketMover,
  SectorPerformance,
  UnusualActivity,
} from '../../../core/models/market-breadth.model';
import { Market } from '../../../core/services/market';

/**
 * Whole-market state: breadth, the day's extremes and how each sector fared.
 *
 * <p>Built to occupy the detail pane when no row is selected. That pane was
 * previously a centred "nothing selected" card in a large empty box — the
 * single worst use of space in the app. The market itself is always doing
 * something, so there is always something true to show there.
 */
@Component({
  selector: 'app-market-pulse',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './market-pulse.html',
  styleUrl: './market-pulse.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MarketPulse {
  /** Suppresses the heading when the host already provides one. */
  readonly compact = input(false);

  private readonly market = inject(Market);

  protected readonly breadth = signal<MarketBreadth | null>(null);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);

  /** Which movers list the toggle is showing. */
  protected readonly moversView = signal<'gainers' | 'losers' | 'active'>('gainers');

  protected readonly unusual = signal<UnusualActivity[]>([]);

  /**
   * Short labels for the server's signal codes. Kept as a lookup rather than a
   * string transform so a new signal type upstream shows its raw code — which
   * is visibly wrong and gets fixed — instead of a plausible-looking guess.
   */
  private static readonly SIGNAL_LABELS: Record<string, string> = {
    VOLUME_SPIKE: 'Volume',
    BREAKOUT_52W_HIGH: '52w high',
    BREAKDOWN_52W_LOW: '52w low',
    LARGE_MOVE: 'Big move',
    WIDE_RANGE: 'Wide range',
  };

  constructor() {
    this.market.getBreadth().subscribe({
      next: (data) => {
        this.breadth.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });

    // Separate request, and a failure here is silent: the breadth summary is
    // the point of this panel, and losing the "unusual" strip should not blank
    // it out. The section simply does not render.
    this.market.getUnusualActivity(6).subscribe({
      next: (rows) => this.unusual.set(rows),
      error: () => this.unusual.set([]),
    });
  }

  protected signalLabel(signal: string): string {
    return MarketPulse.SIGNAL_LABELS[signal] ?? signal;
  }

  /** Down-signals get the negative treatment; everything else is neutral. */
  protected isBearishSignal(signal: string): boolean {
    return signal === 'BREAKDOWN_52W_LOW';
  }

  protected isBullishSignal(signal: string): boolean {
    return signal === 'BREAKOUT_52W_HIGH';
  }

  protected readonly movers = computed<MarketMover[]>(() => {
    const data = this.breadth();
    if (!data) {
      return [];
    }
    switch (this.moversView()) {
      case 'losers':
        return data.topLosers ?? [];
      case 'active':
        return data.mostActive ?? [];
      default:
        return data.topGainers ?? [];
    }
  });

  /**
   * Advancing and declining as shares of the names we could actually price.
   * Unpriced names are excluded from the bar and reported as a separate
   * count — including them would make a thin day look like a flat one.
   */
  protected readonly breadthSplit = computed(() => {
    const data = this.breadth();
    if (!data) {
      return null;
    }
    const priced = data.advancing + data.declining + data.unchanged;
    if (priced === 0) {
      return null;
    }
    return {
      priced,
      advancingPct: (data.advancing / priced) * 100,
      decliningPct: (data.declining / priced) * 100,
      unchangedPct: (data.unchanged / priced) * 100,
    };
  });

  /**
   * Widest absolute sector move in the set, used to scale the bars so the
   * strongest sector fills its track. A fixed scale would flatten every bar
   * to a sliver on a quiet day, when the relative ranking is the whole point.
   */
  protected readonly sectorScale = computed(() => {
    const sectors = this.breadth()?.sectorPerformance ?? [];
    const widest = sectors.reduce(
      (max, sector) => Math.max(max, Math.abs(sector.averageChangePct ?? 0)),
      0,
    );
    // Floor at 0.1 so a completely flat market cannot divide by zero.
    return Math.max(widest, 0.1);
  });

  /**
   * Half-width, because the bar diverges from a centre axis: each side of the
   * track is 50% of it, so the widest move in the set fills exactly one half.
   */
  protected sectorWidth(sector: SectorPerformance): number {
    if (sector.averageChangePct === null) {
      return 0;
    }
    return (Math.abs(sector.averageChangePct) / this.sectorScale()) * 50;
  }

  /** SECTOR_ENUM_NAME -> "Sector Enum Name". */
  protected readable(sector: string): string {
    return sector
      .toLowerCase()
      .split('_')
      .filter(Boolean)
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }

  protected setView(view: 'gainers' | 'losers' | 'active'): void {
    this.moversView.set(view);
  }
}
