import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AdGate, GatedFeature } from '../services/ad-gate';

/**
 * Turns the server's "watch an ad first" into actually showing one.
 *
 * <p>Sitting in the HTTP layer rather than in each page means a gated call is
 * handled wherever it is made - including from code written before the gate
 * existed. A page does not have to know its endpoint is gated; it makes its
 * call, the ad plays, and the call is retried and succeeds.
 *
 * <p>The gate is not enforced here. The server has already refused the
 * request; all this does is offer the user the way through. Deleting this
 * file would make the app worse, not more permissive - the endpoint would
 * still be shut.
 */
export const adGateInterceptor: HttpInterceptorFn = (req, next) => {
  const gate = inject(AdGate);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 402) {
        return throwError(() => error);
      }

      const feature = featureFrom(error);
      if (!feature) {
        // A 402 we do not recognise. Passing it on is right: inventing a gate
        // for an unknown feature would show an ad that unlocks nothing.
        return throwError(() => error);
      }

      return gate.require(feature).pipe(
        switchMap((opened) => {
          if (!opened) {
            // The user declined or the ad failed. The original error stands,
            // so the caller reports it the way it would have anyway.
            return throwError(() => error);
          }
          // Retried once only. The grant cookie is set by now, so a second
          // 402 means something is genuinely wrong - looping on it would
          // trap the user in a cycle of ads that never opens anything.
          return next(req);
        }),
      );
    }),
  );
};

/** The feature named in the gateway's refusal, if it named one we know. */
function featureFrom(error: HttpErrorResponse): GatedFeature | null {
  const named = (error.error as { feature?: string } | null)?.feature;
  const known: GatedFeature[] = [
    'PIPELINE_RUN',
    'ADVANCED_ANALYSIS',
    'PORTFOLIO_ANALYTICS',
    'DATA_EXPORT',
  ];
  return known.includes(named as GatedFeature) ? (named as GatedFeature) : null;
}
