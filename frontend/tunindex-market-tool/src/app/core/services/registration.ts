import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';

export interface RegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  password: string;
}

/**
 * NOTE: mock implementation pending backend integration — see readme.md.
 * The real endpoint is POST /accounts/management/user/create (confirmed
 * public/anonymously reachable — see the backend contract audit). It
 * requires a non-null AddressDto (the backend NPEs on a missing one).
 */
@Injectable({ providedIn: 'root' })
export class Registration {
  create(request: RegistrationRequest): Observable<void> {
    void request;
    return of(undefined).pipe(delay(700));
  }
}
