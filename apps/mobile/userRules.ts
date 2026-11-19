import type { NewUser } from './api/users';

export const userRoles = ['ADMIN', 'BRANCH_MANAGER', 'SALESPERSON'] as const;
export const branchNames = ['Varanasi', 'Ghazipur', 'Mau'] as const;

export function validateNewUser(user: NewUser): string | null {
  if (!user.username.trim() || !/^[a-zA-Z0-9._-]{3,80}$/.test(user.username.trim()))
    return 'Username must be 3–80 letters, digits, dots, underscores or hyphens.';
  if (!user.displayName.trim() || user.displayName.trim().length > 120)
    return 'Enter a display name of at most 120 characters.';
  if (!userRoles.includes(user.role))
    return 'Select a valid role.';
  if (user.role !== 'ADMIN' && !user.branch?.trim())
    return 'Branch is required for managers and salespeople.';
  if (user.password.length < 12 || !/^[\x20-\x7E]+$/.test(user.password))
    return 'Use at least 12 printable ASCII characters for the development password.';
  return null;
}
