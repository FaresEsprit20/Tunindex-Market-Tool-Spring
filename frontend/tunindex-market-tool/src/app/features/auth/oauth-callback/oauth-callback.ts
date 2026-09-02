import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Card } from '../../../shared/components/card/card';
import { Auth } from '../../../core/services/auth';

type CallbackState = 'checking' | 'success' | 'error';

/**
 * Lands here after the backend's server-side Google OAuth2 redirect chain
 * completes. By this point the accessToken/refreshToken cookies are
 * already set by OAuth2LoginSuccessHandler — this page just needs to
 * confirm that via GET /auth/check-auth, not parse anything from the URL.
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

  protected readonly state = signal<CallbackState>('checking');
  protected readonly email = signal<string | null>(null);

  constructor() {
    this.auth.checkAuth().subscribe({
      next: (res) => {
        this.email.set(res.email);
        this.state.set(res.authenticated ? 'success' : 'error');
      },
      error: () => this.state.set('error'),
    });
  }
}
