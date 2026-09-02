import { Injectable, computed, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay, tap } from 'rxjs/operators';

export interface LoginCredentials {
  email: string;
  password: string;
  rememberDevice: boolean;
}

export interface AuthUser {
  email: string;
}

/**
 * NOTE: this is a mock implementation for the design/UI build-out. Real
 * backend integration (POST /auth/authenticate on the `api` service, token
 * storage, refresh flow) is a separate, explicit next step — see readme.md.
 */
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly _currentUser = signal<AuthUser | null>(null);
  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);

  login(credentials: LoginCredentials): Observable<AuthUser> {
    const user: AuthUser = { email: credentials.email };
    return of(user).pipe(
      delay(700),
      tap((authenticated) => this._currentUser.set(authenticated)),
    );
  }

  logout(): void {
    this._currentUser.set(null);
  }
}
