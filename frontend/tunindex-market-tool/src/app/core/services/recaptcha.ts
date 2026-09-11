import { Injectable } from '@angular/core';
import { Observable, from, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { RECAPTCHA_SITE_KEY } from '../config/api.config';

/** The slice of the global reCAPTCHA object this uses. */
interface Grecaptcha {
  ready(callback: () => void): void;
  execute(siteKey: string, options: { action: string }): Promise<string>;
}

declare global {
  interface Window {
    grecaptcha?: Grecaptcha;
  }
}

/**
 * Obtains a reCAPTCHA v3 token for the actions that need one.
 *
 * <p>v3 has no puzzle and no checkbox: it scores the session in the background
 * and returns a token the server exchanges for that score. So there is nothing
 * to render, and the only job here is to have the script loaded by the time a
 * protected call is made.
 *
 * <p>The script is fetched on first use rather than at startup. It is a
 * third-party request on every page load otherwise, and most sessions never
 * touch a protected endpoint - somebody reading the dashboard should not pay
 * for a sign-in defence they are not using.
 */
@Injectable({ providedIn: 'root' })
export class Recaptcha {
  private loader: Promise<Grecaptcha | null> | null = null;

  /**
   * A token for one action, or null if reCAPTCHA could not produce one.
   *
   * <p>Null rather than an error: the caller decides what a missing token
   * means. That decision belongs to the server anyway - the browser refusing
   * to send the request would protect nothing, since anything bypassing this
   * code would simply not call it.
   */
  token(action: string): Observable<string | null> {
    return from(this.execute(action)).pipe(catchError(() => of(null)));
  }

  private async execute(action: string): Promise<string | null> {
    const grecaptcha = await this.load();
    if (!grecaptcha) {
      return null;
    }
    try {
      return await new Promise<string>((resolve, reject) => {
        grecaptcha.ready(() => {
          grecaptcha.execute(RECAPTCHA_SITE_KEY, { action }).then(resolve, reject);
        });
      });
    } catch {
      return null;
    }
  }

  /**
   * Loads the script once, and remembers the attempt either way.
   *
   * <p>Caching the promise rather than the result matters: several protected
   * calls can start together, and without it each would inject its own copy of
   * the script tag.
   */
  private load(): Promise<Grecaptcha | null> {
    if (this.loader) {
      return this.loader;
    }
    this.loader = new Promise<Grecaptcha | null>((resolve) => {
      if (typeof document === 'undefined') {
        resolve(null);
        return;
      }
      if (window.grecaptcha) {
        resolve(window.grecaptcha);
        return;
      }
      const script = document.createElement('script');
      script.src = `https://www.google.com/recaptcha/api.js?render=${RECAPTCHA_SITE_KEY}`;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve(window.grecaptcha ?? null);
      // Resolved rather than rejected: an ad blocker or an offline network
      // should leave the caller with "no token", which it already handles,
      // not an unhandled rejection.
      script.onerror = () => resolve(null);
      document.head.appendChild(script);
    });
    return this.loader;
  }
}
