import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Ads } from '../../../core/services/ads';
import { AdPlacement, Advertisement } from '../../../core/models/ad.model';
import { AdBanner } from '../ad-banner/ad-banner';
import { AdVideo } from '../ad-video/ad-video';
import { AdNative } from '../ad-native/ad-native';
import { AdInterstitial } from '../ad-interstitial/ad-interstitial';

/**
 * One tag a page can drop anywhere, which resolves to whatever format the slot
 * is filled with.
 *
 * <p>Pages ask for a <em>placement</em> and get back whichever unit the
 * backend decided to serve there. Without this, every page embedding an ad
 * would have to know the format in advance, and changing a slot from a banner
 * to a video would mean editing the page rather than the campaign.
 *
 * <p>Renders nothing at all when no ad is eligible - no frame, no placeholder,
 * no reserved height. An empty bordered box advertises that something failed
 * and costs the reader attention while earning nothing.
 */
@Component({
  selector: 'app-ad-slot',
  imports: [AdBanner, AdVideo, AdNative, AdInterstitial],
  templateUrl: './ad-slot.html',
  styleUrl: './ad-slot.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdSlot implements OnInit {
  private readonly ads = inject(Ads);

  readonly placement = input.required<AdPlacement>();

  protected readonly ad = signal<Advertisement | null>(null);
  protected readonly dismissed = signal(false);

  /**
   * Resolved once per mount.
   *
   * <p>In ngOnInit rather than the constructor because the placement is a
   * required input, and inputs are not bound yet when the constructor runs -
   * reading it there throws. Once per mount and not per change-detection
   * pass, or the slot would swap ads while someone was looking at one and
   * report impressions for units nobody saw.
   */
  ngOnInit(): void {
    this.ads.forPlacement(this.placement()).subscribe((advert) => this.ad.set(advert));
  }

  /**
   * Which component renders this unit.
   *
   * <p>Network-managed sources are deliberately excluded from the self-served
   * players: AdSense, Ad Manager and YouTube render through their own scripts,
   * and wrapping them in our video player would neither work nor respect the
   * provider's rules. They fall through to the embed path.
   */
  protected readonly format = computed<'video' | 'banner' | 'native' | 'interstitial' | 'embed' | null>(() => {
    const advert = this.ad();
    if (!advert || this.dismissed()) {
      return null;
    }
    if (advert.source === 'GOOGLE_ADSENSE' || advert.source === 'GOOGLE_AD_MANAGER') {
      return 'embed';
    }
    switch (advert.type) {
      case 'VIDEO_PREROLL':
      case 'VIDEO_MIDROLL':
      case 'VIDEO_POSTROLL':
      case 'REWARDED':
        return 'video';
      case 'NATIVE':
      case 'SPONSORED_CONTENT':
        return 'native';
      case 'INTERSTITIAL':
        return 'interstitial';
      case 'BANNER':
      case 'DISPLAY':
      default:
        return 'banner';
    }
  });

  protected onDismissed(): void {
    this.dismissed.set(true);
  }
}
