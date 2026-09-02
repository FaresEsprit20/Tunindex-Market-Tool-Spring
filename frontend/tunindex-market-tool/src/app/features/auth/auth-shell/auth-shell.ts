import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Theme } from '../../../core/services/theme';

/**
 * Route-level shell for every auth screen: a clean centered layout with the
 * wordmark, a brand panel, and a light/dark toggle. Child auth routes render
 * through <router-outlet> as the centered card. No ticker here — the stock
 * endpoints require a session, so there's no real data to show pre-login;
 * see AppShell for the live ticker.
 */
@Component({
  selector: 'app-auth-shell',
  imports: [RouterOutlet],
  templateUrl: './auth-shell.html',
  styleUrl: './auth-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthShell {
  protected readonly theme = inject(Theme);
}
