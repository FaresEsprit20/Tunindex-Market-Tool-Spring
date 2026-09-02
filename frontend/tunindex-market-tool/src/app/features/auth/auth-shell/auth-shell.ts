import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Theme } from '../../../core/services/theme';
import { MarketTicker } from '../../../shared/components/market-ticker/market-ticker';

/**
 * Route-level shell for every auth screen: a clean centered layout with the
 * wordmark, a live-feeling market ticker strip, and a light/dark toggle.
 * Child auth routes render through <router-outlet> as the centered card.
 */
@Component({
  selector: 'app-auth-shell',
  imports: [RouterOutlet, MarketTicker],
  templateUrl: './auth-shell.html',
  styleUrl: './auth-shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthShell {
  protected readonly theme = inject(Theme);
}
