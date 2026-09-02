// Mirrors backend's RolesDto / UserRole enum.
export type UserRole = 'USER' | 'ADMIN';

export interface RolesDto {
  id: number;
  roleName: UserRole;
}
