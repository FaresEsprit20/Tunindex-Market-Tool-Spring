import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { TotpSetup, TotpStatus } from '../models/totp.model';

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
}
