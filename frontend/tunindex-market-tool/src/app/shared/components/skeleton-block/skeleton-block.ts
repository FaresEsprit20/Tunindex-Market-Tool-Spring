import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Shaped placeholder block with a moving gradient sweep for data-in-flight
 * states. Always shaped to match the eventual layout — never a blank panel
 * or a generic spinner.
 */
@Component({
  selector: 'app-skeleton-block',
  imports: [],
  templateUrl: './skeleton-block.html',
  styleUrl: './skeleton-block.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'skeleton-block-host' },
})
export class SkeletonBlock {
  readonly width = input<string>('100%');
  readonly height = input<string>('14px');
  readonly radius = input<string>('var(--radius-sm)');
}
