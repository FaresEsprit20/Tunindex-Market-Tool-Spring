import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

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
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        console.error(`[API] ${req.method} ${req.url} -> ${error.status}`, error.error);
      }
      return throwError(() => error);
    }),
  );
};
