import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { TotpSetup, TotpStatus, TwoFactorMethod, TwoFactorMethodChange } from '../models/totp.model';

/**
 * Profile-side TOTP enrollment — enabling/disabling two-factor auth for the
 * current user. Distinct from Auth.verifyTwoFactor(), which completes an
 * in-progress *login* once 2FA is already enabled.
 */
@Injectable({ providedIn: 'root' })
export class TwoFactorSetup {
  private readonly http = inject(HttpClient);

  getStatus(): Observable<TotpStatus> {
    return this.http.get<TotpStatus>(`${API_BASE_URL}/account/2fa/status`);
  }

  beginSetup(): Observable<TotpSetup> {
    return this.http.post<TotpSetup>(`${API_BASE_URL}/account/2fa/setup`, {});
  }

  confirmSetup(code: string): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/account/2fa/confirm`, { code });
  }

  disable(code: string): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/account/2fa/disable`, { code });
  }

  /**
   * Begins a switch to another delivery method.
   *
   * <p>Nothing changes until {@link confirmMethodChange} succeeds: the server
   * holds the new method aside and only applies it once a code that genuinely
   * arrived comes back. A channel that cannot deliver therefore leaves the
   * account on its current method instead of locking it out.
   */
  startMethodChange(method: TwoFactorMethod): Observable<TwoFactorMethodChange> {
    return this.http.post<TwoFactorMethodChange>(`${API_BASE_URL}/account/2fa/method/start`, { method });
  }

  confirmMethodChange(code: string): Observable<void> {
    return this.http.post<void>(`${API_BASE_URL}/account/2fa/method/confirm`, { code });
  }
}
