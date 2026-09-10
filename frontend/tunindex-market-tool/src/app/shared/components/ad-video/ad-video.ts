import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { Ads } from '../../../core/services/ads';
import { Advertisement } from '../../../core/models/ad.model';

/**
 * A video ad, with the skip rules the unit was configured with.
 *
 * <p>This is where "non-skippable" is actually enforced, and only for creatives
 * we serve ourselves. A network unit renders through the provider's own player
 * and obeys the provider's rules, so this component is not used for those - it
 * would be pretending to control something it does not.
 *
 * <p>Completion is tracked because it is what gets paid on a per-view deal: a
 * video abandoned halfway earns nothing, and reporting it as a view would bill
 * an advertiser for attention they did not receive.
 */
@Component({
  selector: 'app-ad-video',
  imports: [],
  templateUrl: './ad-video.html',
  styleUrl: './ad-video.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdVideo implements OnDestroy {
  private readonly ads = inject(Ads);

  readonly ad = input.required<Advertisement>();

  protected readonly elapsed = signal(0);
  protected readonly finished = signal(false);

  private timer?: ReturnType<typeof setInterval>;
  private impressionRecorded = false;
  private outcomeRecorded = false;

  /**
   * Whether a skip control should be offered right now.
   *
   * <p>Two conditions, both required: the unit must permit skipping at all,
   * and the configured delay must have passed. A unit marked non-skippable
   * never shows the control regardless of how long it runs.
   */
  protected readonly canSkip = computed(() => {
    const advert = this.ad();
    if (!advert.skippable) {
      return false;
    }
    const after = advert.skipAfterSeconds ?? 0;
    return this.elapsed() >= after;
  });

  /** Seconds until skipping becomes available, for the countdown. */
  protected readonly secondsUntilSkip = computed(() => {
    const after = this.ad().skipAfterSeconds ?? 0;
    return Math.max(0, after - this.elapsed());
  });

  protected readonly remaining = computed(() => {
    const total = this.ad().durationSeconds;
    return total === null ? null : Math.max(0, total - this.elapsed());
  });

  protected onPlaying(): void {
    if (!this.impressionRecorded) {
      this.impressionRecorded = true;
      this.ads.record(this.ad().id, 'IMPRESSION');
    }
    this.startTimer();
  }

  protected onPaused(): void {
    this.stopTimer();
  }

  /** The video ran to the end: the event a per-view deal actually pays for. */
  protected onEnded(): void {
    this.stopTimer();
    this.finished.set(true);
    this.recordOutcome('COMPLETED_VIEW', 100);
  }

  protected onSkip(): void {
    if (!this.canSkip()) {
      return;
    }
    this.stopTimer();
    this.finished.set(true);
    this.recordOutcome('SKIPPED', this.watchedPercent());
  }

  protected onClick(): void {
    this.ads.record(this.ad().id, 'CLICK');
  }

  private watchedPercent(): number {
    const total = this.ad().durationSeconds;
    if (!total || total <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((this.elapsed() / total) * 100));
  }

  /**
   * Reports the outcome exactly once.
   *
   * <p>A video can end and be skipped in quick succession as the element
   * settles; sending both would record two outcomes for one view and
   * double-count whichever the pricing model pays for.
   */
  private recordOutcome(event: 'COMPLETED_VIEW' | 'SKIPPED', percent: number): void {
    if (this.outcomeRecorded) {
      return;
    }
    this.outcomeRecorded = true;
    this.ads.record(this.ad().id, event, percent);
  }

  private startTimer(): void {
    this.stopTimer();
    this.timer = setInterval(() => this.elapsed.update((value) => value + 1), 1000);
  }

  private stopTimer(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = undefined;
    }
  }

  ngOnDestroy(): void {
    // Left running, this keeps ticking after the ad is gone and leaks a timer
    // per navigation.
    this.stopTimer();
  }
}
