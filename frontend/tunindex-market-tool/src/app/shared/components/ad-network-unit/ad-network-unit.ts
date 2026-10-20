import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { AdNetworks } from '../../../core/services/ad-networks';
import { Advertisement } from '../../../core/models/ad.model';

/**
 * A real network ad unit - AdSense or Google Ad Manager.
 *
 * <p>Unlike the self-served formats beside it, nothing here draws a creative
 * or counts an impression. The network decides what to show, measures it, and
 * reports the earnings; anything this component counted would be a second,
 * disagreeing set of numbers, and the network's is the one that gets paid.
 *
 * <p>Renders nothing at all when no publisher id is configured. An empty
 * bordered box on a page is worse than no box: it advertises a failure and
 * earns nothing either way.
 */
@Component({
  selector: 'app-ad-network-unit',
  imports: [],
  templateUrl: './ad-network-unit.html',
  styleUrl: './ad-network-unit.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdNetworkUnit implements AfterViewInit {
  private readonly networks = inject(AdNetworks);

  readonly ad = input.required<Advertisement>();

  private readonly host = viewChild<ElementRef<HTMLElement>>('unit');

  protected readonly ready = signal(false);
  protected readonly publisherId = this.networks.publisherId;

  /** A stable, unique id: Ad Manager addresses its slot by element id. */
  protected readonly domId = `gam-${Math.random().toString(36).slice(2, 10)}`;

  protected readonly isAdsense = () =>
    this.ad().source === 'GOOGLE_ADSENSE' && this.networks.adsenseConfigured;

  protected readonly isAdManager = () =>
    this.ad().source === 'GOOGLE_AD_MANAGER' && this.networks.adManagerConfigured;

  /**
   * Filled after the view exists.
   *
   * <p>Both networks read the element out of the DOM when asked to fill it,
   * so calling either before the template has rendered silently does nothing
   * - the slot simply stays blank with no error to explain why.
   */
  ngAfterViewInit(): void {
    if (this.isAdsense()) {
      void this.networks.loadAdsense().then((loaded) => {
        if (loaded) {
          this.networks.fillAdsenseUnit();
          this.ready.set(true);
        }
      });
      return;
    }

    if (this.isAdManager()) {
      const path = this.ad().externalUnitId;
      if (!path) {
        return;
      }
      void this.networks.loadAdManager().then((loaded) => {
        if (loaded) {
          // Sizes the network is allowed to fill. A responsive set rather
          // than one fixed box, so the same unit works in a sidebar and
          // across the top of a page.
          this.networks.displayAdManagerUnit(this.domId, path, [[300, 250], [728, 90], [320, 100]]);
          this.ready.set(true);
        }
      });
    }
  }

  /** The network's own slot id, for the data-ad-slot attribute. */
  protected get slotId(): string {
    return this.ad().externalUnitId ?? '';
  }
}
