import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subject, of } from 'rxjs';
import { catchError, filter, map, take } from 'rxjs/operators';
import { ADS_BASE_URL } from '../config/api.config';
import { Advertisement } from '../models/ad.model';

/** Mirrors the backend's GatedFeature enum. */
export type GatedFeature =
  | 'PIPELINE_RUN'
  | 'ADVANCED_ANALYSIS'
  | 'PORTFOLIO_ANALYTICS'
  | 'DATA_EXPORT';

/** What the server hands back when a gate opens. */
export interface GateChallenge {
  ad: Advertisement;
  viewToken: string;
  requiredSeconds: number;
  heartbeatSeconds: number;
  feature: GatedFeature;
  featureLabel: string;
}

/**
 * Drives the watch-to-unlock flow.
 *
 * <p>Deliberately thin. None of the rules live here: how long the ad runs,
 * whether it has finished, and whether the feature opens are all the server's
 * decisions, and this only relays them. That separation is the point - code
 * in the browser can be edited by whoever is running it, so anything that
 * decided here would decide nothing at all.
 *
 * <p>It also holds the one piece of shared UI state: which gate, if any, is
 * currently in front of the user. The interceptor puts a feature here when the
 * server refuses a call, the overlay in the shell notices and plays the ad,
 * and the interceptor retries once the gate reports it opened.
 */
@Injectable({ providedIn: 'root' })
export class AdGate {
  private readonly http = inject(HttpClient);

  private readonly active = signal<GatedFeature | null>(null);

  /** The gate the shell should be showing, if any. */
  readonly current = computed(() => this.active());

  /**
   * Fires when a gate finishes, with whether it opened.
   *
   * <p>A subject rather than a promise because the same gate can be raised
   * again later in the session, and a promise only resolves once.
   */
  private readonly outcomes = new Subject<{ feature: GatedFeature; opened: boolean }>();

  /**
   * Raises a gate and reports how it ended.
   *
   * <p>If one is already up for the same feature, the existing one is joined
   * rather than a second raised: several calls to a gated endpoint can fail
   * together, and each opening its own ad would stack them.
   */
  require(feature: GatedFeature): Observable<boolean> {
    if (this.active() === null) {
      this.active.set(feature);
    }
    return this.outcomes.pipe(
      // Only this feature's outcome: another gate resolving must not be
      // mistaken for this one opening.
      filter((outcome) => outcome.feature === feature),
      take(1),
      map((outcome) => outcome.opened),
    );
  }

  /** Called by the overlay when the ad finished, or the user gave up. */
  settle(feature: GatedFeature, opened: boolean): void {
    this.active.set(null);
    this.outcomes.next({ feature, opened });
  }

  // ── Server conversation ─────────────────────────────────────────────

  /**
   * Asks for an ad to show at a gate.
   *
   * <p>Null means there was nothing to play. The caller then proceeds as if
   * the gate had opened: an ad server with no inventory must not become a
   * locked door on the product.
   */
  start(feature: GatedFeature): Observable<GateChallenge | null> {
    return this.http
      .post<GateChallenge>(`${ADS_BASE_URL}/gate/start/${feature}`, {})
      .pipe(catchError(() => of(null)));
  }

  /**
   * Reports that the ad is still playing.
   *
   * <p>Fire and forget. The server ignores check-ins that arrive too quickly,
   * so there is nothing for the client to do about the result, and a dropped
   * one is already within the tolerance the server allows.
   */
  heartbeat(viewToken: string): void {
    this.http
      .post(`${ADS_BASE_URL}/gate/heartbeat`, { viewToken })
      .pipe(catchError(() => of(null)))
      .subscribe();
  }

  /**
   * Claims the grant.
   *
   * <p>False covers every refusal, including "too soon". The overlay's answer
   * to that is the same in each case: keep the ad up. The grant itself never
   * reaches this code - it comes back as an HttpOnly cookie, which is what
   * stops a page script from reading or copying it.
   */
  complete(viewToken: string, feature: GatedFeature): Observable<boolean> {
    return this.http
      .post<{ granted: boolean }>(`${ADS_BASE_URL}/gate/complete`, { viewToken, feature })
      .pipe(
        map((response) => response.granted === true),
        catchError(() => of(false)),
      );
  }

  /** Whether the user can already use a feature without watching anything. */
  status(feature: GatedFeature): Observable<boolean> {
    return this.http
      .get<{ granted: boolean }>(`${ADS_BASE_URL}/gate/status/${feature}`)
      .pipe(
        map((response) => response.granted === true),
        catchError(() => of(false)),
      );
  }
}
