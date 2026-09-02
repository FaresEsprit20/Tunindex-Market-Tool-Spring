import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Dense, bordered label-over-value tile for summary numbers — preferred
 * over a chart when the data's job is a single headline figure.
 */
export type StatTileAccent = 'brand' | 'positive' | 'negative' | 'warning';

@Component({
  selector: 'app-stat-tile',
  imports: [],
  templateUrl: './stat-tile.html',
  styleUrl: './stat-tile.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'stat-tile-host' },
})
export class StatTile {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly trend = input<'up' | 'down' | null>(null);
  readonly trendLabel = input<string | null>(null);
  /** Inline SVG path data (viewBox 0 0 24 24), matching the icon style used across the sidebar/navbar. */
  readonly icon = input<string | null>(null);
  readonly accent = input<StatTileAccent>('brand');
}
