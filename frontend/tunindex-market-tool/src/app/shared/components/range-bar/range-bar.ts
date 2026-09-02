import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Horizontal position-in-range bar (e.g. today's price within the day's
 * low/high, or within the 52-week low/high). Genuinely computed from the
 * min/current/max inputs — no illustrative/placeholder positioning.
 */
@Component({
  selector: 'app-range-bar',
  imports: [],
  templateUrl: './range-bar.html',
  styleUrl: './range-bar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RangeBar {
  readonly min = input.required<number | null>();
  readonly max = input.required<number | null>();
  readonly current = input.required<number | null>();
  readonly minLabel = input<string>('');
  readonly maxLabel = input<string>('');
  readonly compact = input(false);

  protected readonly pct = computed(() => {
    const min = this.min();
    const max = this.max();
    const current = this.current();
    if (min === null || max === null || current === null || max === min) {
      return null;
    }
    const raw = ((current - min) / (max - min)) * 100;
    return Math.max(0, Math.min(100, raw));
  });
}
