import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AddressDto } from '../models/address.model';
import { UserDto, UserExtendedDto } from '../models/user.model';
import { API_BASE_URL } from '../config/api.config';

export interface ProfileUpdateRequest {
  email: string; // must match the authenticated user — the backend 400s otherwise
  firstName: string;
  lastName: string;
  numTel: string;
  photo: string;
  address: AddressDto;
}

export interface ChangeOwnPasswordRequest {
  id: number;
  password: string;
  confirmPassword: string;
}

@Injectable({ providedIn: 'root' })
export class User {
  private readonly http = inject(HttpClient);

  getAuthUser(): Observable<UserExtendedDto> {
    return this.http.get<UserExtendedDto>(`${API_BASE_URL}/users/auth-user`);
  }

  updateProfile(request: ProfileUpdateRequest): Observable<UserDto> {
    return this.http.put<UserDto>(`${API_BASE_URL}/users/update`, request);
  }

  // PUT .../users/update/profile/password (no unused `token` field, unlike
  // the sibling .../users/update/password endpoint which exists for the
  // password-reset flow).
  changeOwnPassword(request: ChangeOwnPasswordRequest): Observable<UserExtendedDto> {
    return this.http.put<UserExtendedDto>(`${API_BASE_URL}/users/update/profile/password`, request);
  }
}
