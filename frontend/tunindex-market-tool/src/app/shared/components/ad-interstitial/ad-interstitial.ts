import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { Ads } from '../../../core/services/ads';
import { Advertisement } from '../../../core/models/ad.model';

/**
 * A full-screen unit shown between two views.
 *
 * <p>The most intrusive format here, so it carries the strictest rules. A
 * dismiss control always appears eventually, even for a unit marked
 * non-skippable: a full-screen overlay with no way out is not an ad, it is a
 * trapped browser tab, and the reader's recourse is to close the site.
 * {@link FORCED_DISMISS_SECONDS} is that backstop.
 */
@Component({
  selector: 'app-ad-interstitial',
  imports: [],
  templateUrl: './ad-interstitial.html',
  styleUrl: './ad-interstitial.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdInterstitial implements OnInit, OnDestroy {
  private readonly ads = inject(Ads);

  readonly ad = input.required<Advertisement>();

  /** Raised when the overlay closes, so the host can resume navigation. */
  readonly closed = output<void>();

  /**
   * The longest a reader can be held, whatever the unit is configured with.
   *
   * <p>A deliberate ceiling on our own configuration. An ad that cannot be
   * dismissed is indistinguishable from a broken page, and the cost is the
   * visit rather than the impression.
   */
  private static readonly FORCED_DISMISS_SECONDS = 10;

  protected readonly elapsed = signal(0);
  private timer?: ReturnType<typeof setInterval>;

  protected readonly canClose = computed(() => {
    const advert = this.ad();
    const configured = advert.skippable ? (advert.skipAfterSeconds ?? 0) : AdInterstitial.FORCED_DISMISS_SECONDS;
    const wait = Math.min(configured, AdInterstitial.FORCED_DISMISS_SECONDS);
    return this.elapsed() >= wait;
  });

  protected readonly secondsLeft = computed(() => {
    const advert = this.ad();
    const configured = advert.skippable ? (advert.skipAfterSeconds ?? 0) : AdInterstitial.FORCED_DISMISS_SECONDS;
    const wait = Math.min(configured, AdInterstitial.FORCED_DISMISS_SECONDS);
    return Math.max(0, wait - this.elapsed());
  });

  ngOnInit(): void {
    this.ads.record(this.ad().id, 'IMPRESSION');
    this.timer = setInterval(() => this.elapsed.update((value) => value + 1), 1000);
  }

  protected onClick(): void {
    this.ads.record(this.ad().id, 'CLICK');
  }

  protected onClose(): void {
    if (!this.canClose()) {
      return;
    }
    this.closed.emit();
  }

  ngOnDestroy(): void {
    if (this.timer) {
      clearInterval(this.timer);
    }
  }
}
