import { HttpClient, HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, shareReplay, switchMap, throwError } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

/**
 * Renews an expired session instead of letting pages fail silently.
 *
 * <p>Access tokens last fifteen minutes and the refresh token a week, but
 * nothing ever spent the refresh token: no interceptor looked at a 401. So
 * fifteen minutes after signing in, every request began returning 401 and each
 * component handled it alone — usually by setting a loading flag to false and
 * rendering nothing. The account page came out completely blank that way, and
 * it was not the only one. Nobody was signed out and nobody was told; the app
 * simply stopped answering.
 *
 * <p>On a 401 this spends the refresh token once and replays the original
 * request. If the refresh fails too, the session is genuinely over and the user
 * is sent to sign in rather than left on a dead page.
 */

/** Endpoints where a 401 is the answer, not a stale session. */
const NEVER_REFRESH = ['/auth/authenticate', '/auth/refresh-token', '/users/logout'];

/**
 * Issues the refresh call.
 *
 * <p>Separate from the Auth service on purpose: Auth makes HTTP calls of its
 * own, so injecting it here would route its traffic back through this
 * interceptor.
 */
@Injectable({ providedIn: 'root' })
export class AuthRefresher {
  private readonly http = inject(HttpClient);

  /**
   * One refresh in flight at a time.
   *
   * <p>A page fires several requests at once and they all return 401
   * together. Without sharing, each would spend the refresh token
   * separately; against a backend that rotates them, the first success
   * invalidates the rest and a recoverable session becomes a forced
   * sign-out.
   */
  private inFlight: Observable<unknown> | null = null;

  refresh(): Observable<unknown> {
    if (!this.inFlight) {
      this.inFlight = this.http
        .post(`${API_BASE_URL}/auth/refresh-token`, {}, { withCredentials: true })
        .pipe(
          shareReplay({ bufferSize: 1, refCount: false }),
          finalize(() => {
            this.inFlight = null;
          }),
        );
    }
    return this.inFlight;
  }
}

export const tokenRefreshInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(API_BASE_URL) || isExempt(req)) {
    return next(req);
  }

  // Resolved here, in the injection context. Calling inject() inside the
  // catchError below would throw, because by then the context is gone.
  const refresher = inject(AuthRefresher);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }

      return refresher.refresh().pipe(
        // Replayed rather than served from a cache: the original call is what
        // the component is still waiting on.
        switchMap(() => next(req)),
        catchError((refreshError: unknown) => {
          router.navigate(['/login'], { queryParams: { expired: 'true' } });
          return throwError(() => refreshError);
        }),
      );
    }),
  );
};

function isExempt(req: HttpRequest<unknown>): boolean {
  return NEVER_REFRESH.some((path) => req.url.includes(path));
}
