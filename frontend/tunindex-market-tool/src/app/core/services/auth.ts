import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

export interface LoginCredentials {
  login: string;
  password: string;
  rememberDevice: boolean;
}

export interface AuthUser {
  email: string;
  userId: number | null;
}

interface AuthenticationResponse {
  accessToken: string | null;
  refreshToken: string | null;
  /** True when the password check passed but a TOTP code is still needed. */
  requiresTwoFactor: boolean;
  mfaToken: string | null;
}

interface AuthCheckResponse {
  authenticated: boolean;
  email: string | null;
  userId: number | null;
}

interface GoogleLoginUrlResponse {
  login_url: string;
  instruction: string;
}

/**
 * Tokens travel as HttpOnly cookies set by the backend (see
 * AuthInterceptor) — this service doesn't store the raw token strings from
 * the response bodies anywhere, it just reacts to whether the browser is
 * currently authenticated per the cookie the server already holds.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly http = inject(HttpClient);

  private readonly _currentUser = signal<AuthUser | null>(null);
  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);

  /**
   * Set only when a login attempt comes back with requiresTwoFactor: true —
   * consumed by the two-factor page to complete the login. Not a real
   * session: no cookies exist yet at this point, so isAuthenticated stays
   * false until /auth/two-factor/verify succeeds.
   */
  private readonly _pendingMfaToken = signal<string | null>(null);
  readonly pendingMfaToken = this._pendingMfaToken.asReadonly();
  // The credential the user typed (email or username) — not necessarily a
  // real email, but good enough as a placeholder until the post-login
  // checkAuth() call (always run by authGuard) fills in the real one.
  private pendingLogin: string | null = null;

  login(credentials: LoginCredentials): Observable<AuthenticationResponse> {
    const { login, password, rememberDevice } = credentials;
    return this.http
      .post<AuthenticationResponse>(`${API_BASE_URL}/auth/authenticate`, {
        login,
        password,
        remember_me: rememberDevice,
      })
      .pipe(
        tap((res) => {
          if (res.requiresTwoFactor) {
            this._pendingMfaToken.set(res.mfaToken);
            this.pendingLogin = login;
          } else {
            this._currentUser.set({ email: login, userId: null });
          }
        }),
      );
  }

  verifyTwoFactor(code: string): Observable<AuthenticationResponse> {
    const mfaToken = this._pendingMfaToken();
    return this.http
      .post<AuthenticationResponse>(`${API_BASE_URL}/auth/two-factor/verify`, { mfaToken, code })
      .pipe(
        tap(() => {
          this._currentUser.set({ email: this.pendingLogin ?? '', userId: null });
          this._pendingMfaToken.set(null);
          this.pendingLogin = null;
        }),
      );
  }

  checkAuth(): Observable<AuthCheckResponse> {
    return this.http.get<AuthCheckResponse>(`${API_BASE_URL}/auth/check-auth`).pipe(
      tap((res) => {
        this._currentUser.set(res.authenticated ? { email: res.email!, userId: res.userId } : null);
      }),
    );
  }

  refreshToken(): Observable<AuthenticationResponse> {
    return this.http.post<AuthenticationResponse>(`${API_BASE_URL}/auth/refresh-token`, {});
  }

  logout(): Observable<string> {
    return this.http
      .post(`${API_BASE_URL}/users/logout`, {}, { responseType: 'text' })
      .pipe(tap(() => this._currentUser.set(null)));
  }

  /**
   * Returns the URL to navigate the WHOLE browser to (not fetch/XHR) — the
   * Google flow is a full redirect chain handled server-side, not an API
   * call whose response you consume directly.
   */
  getGoogleLoginUrl(): Observable<GoogleLoginUrlResponse> {
    return this.http.get<GoogleLoginUrlResponse>(`${API_BASE_URL}/auth/google/login-url`);
  }
}
