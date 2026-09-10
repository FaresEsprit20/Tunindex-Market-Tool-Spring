import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withViewTransitions } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth-interceptor';
import { errorInterceptor } from './core/interceptors/error-interceptor';
import { tokenRefreshInterceptor } from './core/interceptors/token-refresh-interceptor';
import { adGateInterceptor } from './core/interceptors/ad-gate-interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
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
      withInterceptors([authInterceptor, tokenRefreshInterceptor, adGateInterceptor, errorInterceptor]),
    ),
  ],
};
