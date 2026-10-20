import { Injectable } from '@angular/core';
import { ADSENSE_PUBLISHER_ID, AD_MANAGER_NETWORK_CODE } from '../config/api.config';

declare global {
  interface Window {
    adsbygoogle?: unknown[];
    googletag?: {
      cmd: (() => void)[];
      defineSlot?: (path: string, size: number[][], id: string) => { addService: (s: unknown) => unknown } | null;
      pubads?: () => unknown;
      enableServices?: () => void;
      display?: (id: string) => void;
    };
  }
}

/**
 * Loads the ad networks that actually pay.
 *
 * <p>This is the part that was missing. The slot machinery, the placements
 * and the event reporting were all real, but a network unit rendered as an
 * empty bordered box: no provider script was ever fetched, so nothing was
 * requested, nothing was shown and nothing was earned. House ads filled the
 * slots and house ads are worth zero by definition.
 *
 * <p>Both scripts load at most once per page and only when a publisher id is
 * configured. An unconfigured build therefore ships no third-party tag at
 * all, which matters: loading an ad network for a site that has no account
 * costs every visitor a request and returns blank space.
 */
@Injectable({ providedIn: 'root' })
export class AdNetworks {
  private adsenseLoader: Promise<boolean> | null = null;
  private gptLoader: Promise<boolean> | null = null;

  get adsenseConfigured(): boolean {
    return ADSENSE_PUBLISHER_ID.trim().length > 0;
  }

  get adManagerConfigured(): boolean {
    return AD_MANAGER_NETWORK_CODE.trim().length > 0;
  }

  /** Publisher id, for the data-ad-client attribute on each unit. */
  get publisherId(): string {
    return ADSENSE_PUBLISHER_ID.trim();
  }

  get networkCode(): string {
    return AD_MANAGER_NETWORK_CODE.trim();
  }

  /**
   * Ensures the AdSense script is present.
   *
   * <p>Resolves false rather than rejecting when the script cannot load - an
   * ad blocker is the ordinary case, not an error, and a rejected promise
   * here would surface as an unhandled rejection on a page that is otherwise
   * working perfectly.
   */
  loadAdsense(): Promise<boolean> {
    if (!this.adsenseConfigured) {
      return Promise.resolve(false);
    }
    if (this.adsenseLoader) {
      return this.adsenseLoader;
    }
    this.adsenseLoader = this.injectScript(
      `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${encodeURIComponent(this.publisherId)}`,
      // Required by AdSense on every page that serves its ads.
      { crossOrigin: 'anonymous' },
    );
    return this.adsenseLoader;
  }

  /**
   * Hands a rendered <ins> element to AdSense.
   *
   * <p>Pushed once per element and never again. AdSense throws
   * "adsbygoogle.push() error: All ins elements already have ads" if the same
   * element is offered twice, which is easy to trigger in a single-page app
   * where a component can re-render without the DOM node being replaced.
   */
  fillAdsenseUnit(): void {
    try {
      (window.adsbygoogle = window.adsbygoogle || []).push({});
    } catch {
      // Blocked, or the slot was already filled. Neither is worth
      // interrupting the page for.
    }
  }

  /** Ensures Google Publisher Tag is present, for Ad Manager inventory. */
  loadAdManager(): Promise<boolean> {
    if (!this.adManagerConfigured) {
      return Promise.resolve(false);
    }
    if (this.gptLoader) {
      return this.gptLoader;
    }
    this.gptLoader = this.injectScript(
      'https://securepubads.g.doubleclick.net/tag/js/gpt.js',
      { crossOrigin: 'anonymous' },
    ).then((ok) => {
      if (ok) {
        window.googletag = window.googletag || { cmd: [] };
      }
      return ok;
    });
    return this.gptLoader;
  }

  /**
   * Defines and displays one Ad Manager slot.
   *
   * <p>Queued through googletag.cmd rather than called directly, because the
   * library replaces that array with a real command runner once it has
   * loaded; pushing is the documented way to be safe either side of that.
   */
  displayAdManagerUnit(divId: string, unitPath: string, sizes: number[][]): void {
    const googletag = window.googletag;
    if (!googletag) {
      return;
    }
    googletag.cmd.push(() => {
      try {
        const slot = googletag.defineSlot?.(unitPath, sizes, divId);
        if (slot && googletag.pubads) {
          slot.addService(googletag.pubads());
        }
        googletag.enableServices?.();
        googletag.display?.(divId);
      } catch {
        // A malformed unit path or a duplicate div id. The slot stays empty.
      }
    });
  }

  private injectScript(src: string, attrs: { crossOrigin?: string } = {}): Promise<boolean> {
    return new Promise<boolean>((resolve) => {
      if (typeof document === 'undefined') {
        resolve(false);
        return;
      }
      if (document.querySelector(`script[src="${src}"]`)) {
        resolve(true);
        return;
      }
      const script = document.createElement('script');
      script.src = src;
      script.async = true;
      if (attrs.crossOrigin) {
        script.crossOrigin = attrs.crossOrigin;
      }
      script.onload = () => resolve(true);
      // An ad blocker cancels the request. Resolving false lets the caller
      // leave the slot collapsed rather than showing a broken frame.
      script.onerror = () => resolve(false);
      document.head.appendChild(script);
    });
  }
}
