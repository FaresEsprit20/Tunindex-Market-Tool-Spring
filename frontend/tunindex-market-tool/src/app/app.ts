import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastContainer } from './shared/components/toast-container/toast-container';
import { Theme } from './core/services/theme';
import { Auth } from './core/services/auth';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastContainer],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  // Injected for its side effect: applies data-theme to <html> on bootstrap
  // (before first paint of any route) and keeps it in sync thereafter.
  private readonly theme = inject(Theme);
  private readonly auth = inject(Auth);

  constructor() {
    // Restore session state from the accessToken cookie, if any, so the
    // app knows whether a returning visitor is already signed in.
    this.auth.checkAuth().subscribe({ error: () => undefined });
  }
}
