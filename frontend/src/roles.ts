// The product has no login. A role is just a UI persona the header switches
// between; it carries no session or permission. The Admin console is the only
// dark "control room" theme — every business role is light. Role names are
// translated via the i18n catalog (`role.<id>`).
export type RoleId =
  | 'admin'
  | 'retailer'
  | 'media-manager'
  | 'airline'
  | 'passenger';

export type Theme = 'dark' | 'light';

export interface Role {
  id: RoleId;
  theme: Theme;
}

export const ROLES: Role[] = [
  { id: 'admin', theme: 'dark' },
  { id: 'retailer', theme: 'light' },
  { id: 'media-manager', theme: 'light' },
  { id: 'airline', theme: 'light' },
  { id: 'passenger', theme: 'light' },
];

export const DEFAULT_ROLE: RoleId = 'admin';

export function themeForRole(roleId: RoleId): Theme {
  return ROLES.find((role) => role.id === roleId)?.theme ?? 'light';
}
