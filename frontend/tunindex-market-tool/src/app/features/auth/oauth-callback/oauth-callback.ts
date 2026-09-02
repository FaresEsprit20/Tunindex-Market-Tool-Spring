import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Card } from '../../../shared/components/card/card';
import { Auth } from '../../../core/services/auth';

type CallbackState = 'checking' | 'success' | 'error';

/**
 * Lands here after the backend's server-side Google OAuth2 redirect chain
 * completes. OAuth2LoginSuccessHandler both sets the accessToken/
 * refreshToken cookies AND appends them as query params (by design, per
 * its own comment: "for Angular to capture"). We don't need the raw query
 * values — confirming via GET /auth/check-auth (cookie-based) is enough —
 * but we strip them from the URL bar immediately either way, since an
 * access token sitting in the visible URL/history is bad hygiene even for
 * the brief moment before the user navigates away.
 */
@Component({
  selector: 'app-oauth-callback',
  imports: [RouterLink, Card],
  templateUrl: './oauth-callback.html',
  styleUrl: './oauth-callback.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OauthCallback {
  private readonly auth = inject(Auth);
  private readonly router = inject(Router);

  protected readonly state = signal<CallbackState>('checking');
  protected readonly email = signal<string | null>(null);

  constructor() {
    void this.router.navigate([], { queryParams: {}, replaceUrl: true });

    this.auth.checkAuth().subscribe({
      next: (res) => {
        this.email.set(res.email);
        this.state.set(res.authenticated ? 'success' : 'error');
      },
      error: () => this.state.set('error'),
    });
  }
}
