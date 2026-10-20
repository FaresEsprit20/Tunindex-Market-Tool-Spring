import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withViewTransitions } from '@angular/router';
import { routes } from './app.routes';
import { Auth } from './core/services/auth';
import { authInterceptor } from './core/interceptors/auth-interceptor';
import { errorInterceptor } from './core/interceptors/error-interceptor';
import { tokenRefreshInterceptor } from './core/interceptors/token-refresh-interceptor';
import { adGateInterceptor } from './core/interceptors/ad-gate-interceptor';
import { recaptchaInterceptor } from './core/interceptors/recaptcha-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Ask the server who is signed in, once, at startup.
    //
    // Auth.checkAuth() is the only thing that turns the session cookie into a
    // populated `currentUser`, and nothing was calling it. The session
    // survived a reload, but the app's knowledge of it did not: `currentUser`
    // was set during a sign-in and lost on the next page load. Anything keyed
    // on identity was quietly degraded as a result — Watchlist fetches on
    // `isAuthenticated` flipping true, so a signed-in user's watchlist came
    // back empty on every fresh load.
    //
    // Deliberately not awaited. Returning the observable would block the
    // first paint on a network round trip; every consumer already reacts to
    // the signal, so letting it resolve a moment later costs nothing and
    // keeps a slow or failing auth call from holding up the whole app.
    provideAppInitializer(() => {
      inject(Auth).checkAuth().subscribe({ error: () => undefined });
    }),
    // Native View Transitions on every navigation — see the
    // ::view-transition-* rules in styles.scss for the actual motion (a
    // quick cross-fade + rise, not the browser's default plain cross-fade).
    // skipInitialTransition avoids animating the very first paint on load.
    provideRouter(routes, withViewTransitions({ skipInitialTransition: true })),
    // Order matters. The ad gate sits after the token refresh, so an expired
    // session is renewed and the call retried before anything concludes the
    // user owes an ad; and before the error interceptor, so a 402 is turned
    // into a playable ad rather than surfacing to the user as a failure.
    provideHttpClient(
      withInterceptors([
        // First: the token has to be on the request before anything else
        // looks at it, and it must survive a replay by the refresh
        // interceptor below.
        recaptchaInterceptor,
        authInterceptor,
        tokenRefreshInterceptor,
        adGateInterceptor,
        errorInterceptor,
      ]),
    ),
  ],
};
