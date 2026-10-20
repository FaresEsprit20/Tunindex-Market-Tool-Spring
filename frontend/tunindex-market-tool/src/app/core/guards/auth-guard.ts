import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { Auth } from '../services/auth';

/**
 * Confirms the session on every protected navigation (rather than trusting
 * a possibly-stale isAuthenticated() signal) — the cookie backing it can
 * expire between app bootstrap and a later navigation, and this is the
 * same cheap GET /auth/check-auth call either way.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);

  return auth.checkAuth().pipe(
    map((res) => (res.authenticated ? true : router.createUrlTree(['/auth/login']))),
    catchError(() => of(router.createUrlTree(['/auth/login']))),
  );
};
