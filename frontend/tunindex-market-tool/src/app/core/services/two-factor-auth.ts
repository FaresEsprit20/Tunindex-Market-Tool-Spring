import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';

/**
 * NOTE: mock implementation — CONFIRMED there is no public backend endpoint.
 * The backend's 2FA controller lives under /internal/2fa/**, API-key gated
 * for service-to-service calls only, and /auth/authenticate never triggers
 * 2FA at all (verified by reading AuthenticationServiceImpl in full — no
 * TwoFactorAuthService reference, no requires2fa field on the response).
 * This screen has nothing real to integrate with until new backend work
 * adds a public verify/resend endpoint — see readme.md.
 */
@Injectable({ providedIn: 'root' })
export class TwoFactorAuth {
  verify(code: string): Observable<void> {
    void code;
    return of(undefined).pipe(delay(600));
  }

  resend(): Observable<void> {
    return of(undefined).pipe(delay(400));
  }
}
