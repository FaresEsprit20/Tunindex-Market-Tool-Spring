import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Continuous expanding/fading ring around a solid core dot — the signature
 * treatment for any live/streaming data source. Never single-shot: this is
 * the "still live" signal, not a "just changed" one (see FlashOnUpdate).
 */
@Component({
  selector: 'app-pulse-dot',
  imports: [],
  templateUrl: './pulse-dot.html',
  styleUrl: './pulse-dot.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'pulse-dot-host',
    '[class.size-sm]': "size() === 'sm'",
  },
})
export class PulseDot {
  readonly variant = input<'brand' | 'positive' | 'negative'>('positive');
  readonly size = input<'sm' | 'md'>('md');

  protected readonly colorVar = computed(() => `var(--color-${this.variant()})`);
}
