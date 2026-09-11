import { HttpInterceptorFn } from '@angular/common/http';
import { ADS_BASE_URL, API_BASE_URL } from '../config/api.config';

/**
 * The backend issues opaque auth tokens as HttpOnly cookies (accessToken /
 * refreshToken) alongside CORS configured for credentialed requests from
 * this dev origin. Sending cookies automatically like this — rather than
 * managing a Bearer token by hand in JS — is the natural fit here, since
 * HttpOnly cookies can't be read from JS anyway.
 *
 * <p>Every base the platform serves is listed, not just the stock API. A
 * cross-origin request carries no cookies unless it asks to, so a base
 * missing from this list is sent anonymously - and the failure is invisible
 * from the browser: the gateway derives no session, the service answers 401,
 * and a caller that degrades quietly just renders nothing.
 *
 * <p>That is exactly what happened to the ad slots. They live under
 * /api/v1/ads rather than the stock path, so they were excluded here, went
 * out without cookies, and every placement on every page came back empty with
 * no error anywhere.
 */
const CREDENTIALED_BASES = [API_BASE_URL, ADS_BASE_URL];

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!CREDENTIALED_BASES.some((base) => req.url.startsWith(base))) {
    return next(req);
  }
  return next(req.clone({ withCredentials: true }));
};
