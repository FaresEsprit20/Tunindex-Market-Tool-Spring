import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

export interface LoginCredentials {
  email: string;
  password: string;
  rememberDevice: boolean;
}

export interface AuthUser {
  email: string;
  userId: number | null;
}

interface AuthenticationResponse {
  accessToken: string;
  refreshToken: string | null;
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

  login(credentials: LoginCredentials): Observable<AuthenticationResponse> {
    const { email, password, rememberDevice } = credentials;
    return this.http
      .post<AuthenticationResponse>(`${API_BASE_URL}/auth/authenticate`, {
        login: email,
        password,
        remember_me: rememberDevice,
      })
      .pipe(tap(() => this._currentUser.set({ email, userId: null })));
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
