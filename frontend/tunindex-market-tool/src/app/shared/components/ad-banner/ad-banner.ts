import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Ads } from '../../../core/services/ads';
import { Advertisement } from '../../../core/models/ad.model';

/**
 * A static image or display unit.
 *
 * <p>The simplest format and the one with nothing to skip - it either renders
 * or it does not. The impression is recorded once when it appears rather than
 * on every change detection pass, which would otherwise inflate the count and
 * with it the invoice.
 */
@Component({
  selector: 'app-ad-banner',
  imports: [],
  templateUrl: './ad-banner.html',
  styleUrl: './ad-banner.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdBanner {
  private readonly ads = inject(Ads);

  readonly ad = input.required<Advertisement>();

  private recorded = false;

  /**
   * Called from the image's load handler.
   *
   * <p>Tied to the creative actually loading rather than to the component
   * being created: an image that fails to load was never seen, and counting
   * it would bill an advertiser for a blank space.
   */
  protected onLoaded(): void {
    if (this.recorded) {
      return;
    }
    this.recorded = true;
    this.ads.record(this.ad().id, 'IMPRESSION');
  }

  protected onClick(): void {
    this.ads.record(this.ad().id, 'CLICK');
  }
}
