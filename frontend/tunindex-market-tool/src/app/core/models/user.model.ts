import { AddressDto } from './address.model';
import { RolesDto } from './role.model';

// Mirrors backend's UserDto (password is @JsonIgnore'd — never present).
export interface UserDto {
  firstName: string;
  lastName: string;
  email: string;
  numTel: string;
  birthDate: string | null;
  photo: string | null;
  roles: RolesDto[];
  address: AddressDto | null;
}

// UserExtendedDto = UserDto + id.
export interface UserExtendedDto extends UserDto {
  id: number;
}
