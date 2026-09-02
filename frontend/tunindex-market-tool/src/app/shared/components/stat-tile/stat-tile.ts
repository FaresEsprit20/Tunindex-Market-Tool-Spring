import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Dense, bordered label-over-value tile for summary numbers — preferred
 * over a chart when the data's job is a single headline figure.
 */
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
}
