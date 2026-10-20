import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Ads } from '../../../core/services/ads';
import { Advertisement } from '../../../core/models/ad.model';
import { AdInterstitial } from '../ad-interstitial/ad-interstitial';

/**
 * The between-pages ad, mounted once in the shell.
 *
 * <p>Kept out of {@link AdSlot} because it is not a slot: nothing on any page
 * reserves space for it, and it is triggered by moving between pages rather
 * than by a page rendering. Putting it in the shell means it is mounted once
 * and survives navigation, which is exactly what it has to do to observe one.
 *
 * <p>The cooldown is the whole reason this is safe to ship. Without it, every
 * click in the app would raise a full-screen ad.
 */
@Component({
  selector: 'app-ad-navigation-interstitial',
  imports: [AdInterstitial],
  templateUrl: './ad-navigation-interstitial.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdNavigationInterstitial {
  private readonly ads = inject(Ads);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Fifteen minutes between full-screen ads.
   *
   * <p>Long enough that a reader working through the app meets one rarely,
   * short enough that a long session is still worth something. This is the
   * number to change if the balance is wrong; nothing else needs touching.
   */
  private static readonly COOLDOWN_MS = 15 * 60 * 1000;

  /** Navigations to ignore before the first ad can appear. */
  private static readonly GRACE_NAVIGATIONS = 3;

  protected readonly ad = signal<Advertisement | null>(null);
  private navigations = 0;

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.onNavigated());
  }

  private onNavigated(): void {
    this.navigations += 1;

    // Not on arrival, and not on the first couple of moves. Someone who has
    // just signed in is finding their way around; interrupting them there is
    // the moment they are most likely to close the tab.
    if (this.navigations <= AdNavigationInterstitial.GRACE_NAVIGATIONS) {
      return;
    }

    // An interstitial already up means a navigation happened behind it; do
    // not stack a second one on top.
    if (this.ad() !== null) {
      return;
    }

    this.ads
      .forPlacementThrottled('INTERSTITIAL_ON_NAVIGATION', AdNavigationInterstitial.COOLDOWN_MS)
      .subscribe((advert) => this.ad.set(advert));
  }

  protected onClosed(): void {
    this.ad.set(null);
  }
}
