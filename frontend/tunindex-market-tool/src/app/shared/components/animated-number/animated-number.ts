import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, signal } from '@angular/core';

/**
 * Counts a real figure up (or down) to its value instead of snapping to it.
 * Purely presentational: the number that lands is exactly the value passed
 * in — the tween only affects the frames in between, and reduced-motion
 * users get the final value immediately with no animation at all.
 */
@Component({
  selector: 'app-animated-number',
  imports: [],
  template: `{{ display() }}`,
  styles: [':host { font-variant-numeric: tabular-nums; }'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnimatedNumber {
  readonly value = input.required<number>();
  readonly decimals = input(0);
  readonly durationMs = input(750);
  /** Renders a leading "+" for positive values, the way price deltas read. */
  readonly signed = input(false);

  private readonly current = signal(0);
  private frameId: number | null = null;

  protected readonly display = computed(() => {
    const value = this.current();
    const decimals = this.decimals();
    const formatted = value.toLocaleString('en-US', {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
    });
    return this.signed() && value > 0 ? `+${formatted}` : formatted;
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => this.cancel());

    effect(() => {
      const target = this.value();
      const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      if (reducedMotion || this.durationMs() <= 0) {
        this.cancel();
        this.current.set(target);
        return;
      }
      this.animateTo(target);
    });
  }

  private animateTo(target: number): void {
    this.cancel();
    const from = this.current();
    if (from === target) return;

    const duration = this.durationMs();
    const start = performance.now();

    const step = (now: number): void => {
      const elapsed = now - start;
      const t = Math.min(1, elapsed / duration);
      // easeOutExpo — fast out of the gate, settling gently on the value.
      const eased = t === 1 ? 1 : 1 - Math.pow(2, -10 * t);
      this.current.set(from + (target - from) * eased);

      if (t < 1) {
        this.frameId = requestAnimationFrame(step);
      } else {
        this.frameId = null;
        // Land on the exact value, never a rounding artifact of the tween.
        this.current.set(target);
      }
    };

    this.frameId = requestAnimationFrame(step);
  }

  private cancel(): void {
    if (this.frameId !== null) {
      cancelAnimationFrame(this.frameId);
      this.frameId = null;
    }
  }
}
