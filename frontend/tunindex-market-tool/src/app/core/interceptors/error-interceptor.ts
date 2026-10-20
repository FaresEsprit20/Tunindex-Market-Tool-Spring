import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, retry, throwError, timer } from 'rxjs';

const MAX_RATE_LIMIT_RETRIES = 2;

/**
 * Backend error bodies (from RestExceptionHandler) look like:
 *   { httpCode, code, message, errors: string[] }
 * Not every failure path produces this shape though — e.g. a locked
 * account throws Spring Security's LockedException uncaught, so this
 * interceptor only normalizes what it can and otherwise passes the raw
 * HttpErrorResponse through for the caller to handle.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    // A 429 is expected, transient backend behavior (RateLimitingFilter,
    // 10/sec + 100/min per IP) — not a real failure worth surfacing as
    // "couldn't load" on the first hit. The backend's own retry_after_seconds
    // can be up to a full minute (the per-minute bucket), which would make
    // the UI feel frozen if honored literally; a couple of short, capped
    // retries recovers the common per-second burst instead, and just falls
    // through to the normal error path below if the limit is still active.
    retry({
      count: MAX_RATE_LIMIT_RETRIES,
      delay: (error: unknown, retryCount) => {
        if (error instanceof HttpErrorResponse && error.status === 429) {
          return timer(Math.min(800 * retryCount, 2000));
        }
        return throwError(() => error);
      },
    }),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        console.error(`[API] ${req.method} ${req.url} -> ${error.status}`, error.error);
      }
      return throwError(() => error);
    }),
  );
};
