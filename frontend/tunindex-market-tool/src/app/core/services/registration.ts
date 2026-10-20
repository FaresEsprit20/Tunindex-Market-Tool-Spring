import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

export interface RegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  username?: string;
  /** ISO date string (yyyy-MM-dd), as produced by <input type="date">. */
  birthDate: string;
  phone: string;
  password: string;
}

interface AddressDto {
  address1: string;
  address2: string;
  city: string;
  zipCode: string;
  country: string;
}

interface RegisterRequestPayload {
  firstName: string;
  lastName: string;
  email: string;
  username?: string;
  birthDate: string;
  numTel: string;
  password: string;
  photo: string;
  address: AddressDto;
}

interface UserDto {
  firstName: string;
  lastName: string;
  email: string;
  numTel: string;
}

/**
 * POST /accounts/management/user/create — confirmed public/anonymously
 * reachable (see the backend security audit) and confirmed to assign only
 * the USER role. `address` must be a non-null object: the backend
 * dereferences it unconditionally and NPEs on a missing one, so an empty
 * placeholder is sent since this form doesn't collect a postal address.
 */
@Injectable({ providedIn: 'root' })
export class Registration {
  private readonly http = inject(HttpClient);

  create(request: RegistrationRequest): Observable<UserDto> {
    const payload: RegisterRequestPayload = {
      firstName: request.firstName,
      lastName: request.lastName,
      email: request.email,
      username: request.username?.trim() ? request.username.trim() : undefined,
      birthDate: request.birthDate,
      numTel: request.phone,
      password: request.password,
      photo: '',
      address: { address1: '', address2: '', city: '', zipCode: '', country: '' },
    };

    return this.http.post<UserDto>(`${API_BASE_URL}/accounts/management/user/create`, payload);
  }
}
