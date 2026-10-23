// The product has no login. A role is just a UI persona the header switches
// between; it carries no session or permission. The Admin console is the only
// dark "control room" theme — every business role is light.
export type RoleId =
  | 'admin'
  | 'retailer'
  | 'media-manager'
  | 'airline'
  | 'passenger';

export type Theme = 'dark' | 'light';

export interface Role {
  id: RoleId;
  label: string;
  theme: Theme;
}

export const ROLES: Role[] = [
  { id: 'admin', label: 'Platform Admin', theme: 'dark' },
  { id: 'retailer', label: 'Retailer', theme: 'light' },
  { id: 'media-manager', label: 'Media Manager', theme: 'light' },
  { id: 'airline', label: 'Airline Representative', theme: 'light' },
  { id: 'passenger', label: 'Passenger Screen', theme: 'light' },
];

export const DEFAULT_ROLE: RoleId = 'admin';

export function themeForRole(roleId: RoleId): Theme {
  return ROLES.find((role) => role.id === roleId)?.theme ?? 'light';
}

export function labelForRole(roleId: RoleId): string {
  return ROLES.find((role) => role.id === roleId)?.label ?? '';
}
