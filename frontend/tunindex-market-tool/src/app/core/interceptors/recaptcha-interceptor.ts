import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { switchMap } from 'rxjs/operators';
import { RECAPTCHA_PROTECTED_PATHS } from '../config/api.config';
import { Recaptcha } from '../services/recaptcha';

/**
 * Attaches a reCAPTCHA token to the handful of calls a bot would target.
 *
 * <p>Done in the HTTP layer so no sign-in or sign-up form has to remember to
 * do it, and so the list of protected endpoints lives in one place rather than
 * being spread across components that each might get it wrong.
 *
 * <p>Applies to the listed paths only. Requiring a token on every write would
 * put a third-party round trip in front of ordinary in-app actions and take
 * them all down whenever Google is unreachable - for no gain, since those
 * endpoints already require a session.
 */
export const recaptchaInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isProtected(req.url)) {
    return next(req);
  }

  const recaptcha = inject(Recaptcha);

  return recaptcha.token(actionFor(req.url)).pipe(
    switchMap((token) => {
      if (!token) {
        // Sent without a token rather than blocked here. Whether a missing
        // token is fatal is the server's call, and it is the only side that
        // can enforce it - a bot would not be running this code at all.
        return next(req);
      }
      return next(req.clone({ setHeaders: { 'X-Recaptcha-Token': token } }));
    }),
  );
};

function isProtected(url: string): boolean {
  return RECAPTCHA_PROTECTED_PATHS.some((path) => url.includes(path));
}

/**
 * The action name reported to reCAPTCHA.
 *
 * <p>Worth setting per endpoint: Google scores actions separately, so a site
 * that labels everything "submit" learns far less about which flow is being
 * abused than one that distinguishes a sign-in from a password reset.
 */
function actionFor(url: string): string {
  if (url.includes('/auth/authenticate')) return 'login';
  if (url.includes('/auth/two-factor')) return 'two_factor';
  if (url.includes('/password-reset')) return 'password_reset';
  if (url.includes('create')) return 'register';
  return 'submit';
}
