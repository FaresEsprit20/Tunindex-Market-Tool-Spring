import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { AnimatedNumber } from '../animated-number/animated-number';

/**
 * Dense, bordered label-over-value tile for summary numbers — preferred
 * over a chart when the data's job is a single headline figure.
 */
export type StatTileAccent = 'brand' | 'positive' | 'negative' | 'warning';

@Component({
  selector: 'app-stat-tile',
  imports: [AnimatedNumber],
  templateUrl: './stat-tile.html',
  styleUrl: './stat-tile.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'stat-tile-host' },
})
export class StatTile {
  readonly label = input.required<string>();
  /** Pre-formatted display text. Ignored when numericValue is supplied. */
  readonly value = input('');
  /**
   * Supply this instead of `value` for a plain figure and the tile counts
   * up to it on load (and re-counts whenever it changes) rather than
   * snapping. Anything already carrying units or currency should keep
   * using `value`, which renders verbatim.
   */
  readonly numericValue = input<number | null>(null);
  readonly decimals = input(0);
  /** Unit rendered right after an animated numericValue, e.g. " TND" or "%". */
  readonly suffix = input('');
  /** Renders a leading "+" on positive numericValues, for P&L-style figures. */
  readonly signed = input(false);
  readonly trend = input<'up' | 'down' | null>(null);
  readonly trendLabel = input<string | null>(null);
  /** Inline SVG path data (viewBox 0 0 24 24), matching the icon style used across the sidebar/navbar. */
  readonly icon = input<string | null>(null);
  readonly accent = input<StatTileAccent>('brand');
}
