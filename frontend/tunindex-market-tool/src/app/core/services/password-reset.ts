import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';

/**
 * NOTE: mock implementation — CONFIRMED there is no public backend endpoint.
 * Password reset only exists under /internal/password-reset/**, API-key
 * gated for service-to-service calls. The emailed reset link even points at
 * a path (/api/v1/password-reset/verify) that no controller in this
 * codebase serves. A new public controller is needed before this can be
 * wired up for real — see readme.md.
 */
@Injectable({ providedIn: 'root' })
export class PasswordReset {
  requestReset(email: string): Observable<void> {
    void email;
    return of(undefined).pipe(delay(600));
  }

  confirmReset(token: string, newPassword: string): Observable<void> {
    void token;
    void newPassword;
    return of(undefined).pipe(delay(600));
  }
}
