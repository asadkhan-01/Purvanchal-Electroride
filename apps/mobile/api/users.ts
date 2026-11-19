import { request } from './enquiries';
import type { CurrentUser } from './enquiries';

export type UserView = CurrentUser & { id: number; enabled: boolean };
export type NewUser = Pick<CurrentUser, 'username' | 'displayName' | 'role' | 'branch'> & { password: string };

export const usersApi = {
  list: () => request<UserView[]>('/api/v1/users'),
  create: (user: NewUser) => request<UserView>('/api/v1/users', {
    method: 'POST',
    body: JSON.stringify(user),
  }),
};
