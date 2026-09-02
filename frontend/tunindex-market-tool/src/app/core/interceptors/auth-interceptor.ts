import { HttpInterceptorFn } from '@angular/common/http';
import { API_BASE_URL } from '../config/api.config';

/**
 * The backend issues opaque auth tokens as HttpOnly cookies (accessToken /
 * refreshToken) alongside CORS configured for credentialed requests from
 * this dev origin. Sending cookies automatically like this — rather than
 * managing a Bearer token by hand in JS — is the natural fit here, since
 * HttpOnly cookies can't be read from JS anyway.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(API_BASE_URL)) {
    return next(req);
  }
  return next(req.clone({ withCredentials: true }));
};
