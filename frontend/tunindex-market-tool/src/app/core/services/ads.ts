import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { ADS_BASE_URL } from '../config/api.config';
import { AdEventType, AdPlacement, Advertisement } from '../models/ad.model';

/**
 * Fetching ads and reporting what happened to them.
 *
 * <p>Every call here fails quietly. An ad that cannot be loaded should leave
 * the page exactly as it would have been without it - an error banner where a
 * banner ad was meant to go is worse than an empty space, and a failed
 * impression report is not worth interrupting a reader over.
 */
@Injectable({ providedIn: 'root' })
export class Ads {
  private readonly http = inject(HttpClient);

  /**
   * The ad for a slot, or null when there is nothing to show.
   *
   * <p>The backend answers 204 when no ad is eligible, which arrives here as
   * an empty body. Null is returned so the caller can collapse the slot
   * rather than reserving space for something that is not coming.
   */
  forPlacement(placement: AdPlacement): Observable<Advertisement | null> {
    return this.http
      .get<Advertisement>(`${ADS_BASE_URL}/serve/${placement}`)
      .pipe(catchError(() => of(null)));
  }

  /**
   * The ad for a slot, but at most once per cooldown window.
   *
   * <p>For the interruptive placements. The backend has no frequency cap: it
   * answers with the highest-priority eligible ad every time it is asked, so
   * a slot that asks on every navigation would put a full-screen ad in front
   * of a reader on every click. That is not monetisation, it is an unusable
   * app - and a reader who leaves stops earning us anything at all.
   *
   * <p>The window is held in sessionStorage rather than in this service, so
   * it survives a page reload. Losing it (private mode, storage disabled)
   * only means the next interstitial comes sooner than intended, never that
   * the page breaks - hence the swallowed errors.
   */
  forPlacementThrottled(placement: AdPlacement, cooldownMs: number): Observable<Advertisement | null> {
    const key = `ad-cooldown:${placement}`;
    const last = this.readNumber(key);
    if (last !== null && Date.now() - last < cooldownMs) {
      return of(null);
    }
    return this.forPlacement(placement).pipe(
      tap((ad) => {
        // Stamped only when an ad was actually shown. Stamping on an empty
        // response would start the cooldown for something nobody saw, and
        // an unfilled slot would suppress the next real one.
        if (ad) {
          this.writeNumber(key, Date.now());
        }
      }),
    );
  }

  private readNumber(key: string): number | null {
    try {
      const raw = sessionStorage.getItem(key);
      if (raw === null) {
        return null;
      }
      const value = Number(raw);
      return Number.isFinite(value) ? value : null;
    } catch {
      return null;
    }
  }

  private writeNumber(key: string, value: number): void {
    try {
      sessionStorage.setItem(key, String(value));
    } catch {
      // Storage unavailable. The cooldown is a courtesy, not a correctness
      // requirement; failing to persist it must not break the page.
    }
  }

  /**
   * Reports an impression, view, skip, click or conversion.
   *
   * <p>Fire-and-forget: the caller does not wait on it and does not care if
   * it fails. Revenue reporting must never delay or block what the reader is
   * actually looking at.
   */
  record(adId: number, eventType: AdEventType, watchedPercent?: number): void {
    this.http
      .post(`${ADS_BASE_URL}/${adId}/events`, {
        eventType,
        watchedPercent: watchedPercent ?? null,
        deviceCategory: this.deviceCategory(),
      })
      .pipe(catchError(() => of(null)))
      .subscribe();
  }

  /** Coarse enough to be useful for reporting without identifying anyone. */
  private deviceCategory(): string {
    const width = typeof window === 'undefined' ? 1920 : window.innerWidth;
    if (width < 640) return 'mobile';
    if (width < 1024) return 'tablet';
    return 'desktop';
  }
}
