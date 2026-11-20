import { useEffect, useState } from 'react';
import { request } from './enquiries';

export type Branch = { name: string; invoiceAddress: string };

let cache: Branch[] | null = null;
let pending: Promise<Branch[]> | null = null;

export const branchApi = {
  list: () => request<Branch[]>('/api/v1/branches'),
};

/** Active branch names from the server (loaded once per app session). */
export function useBranches(): string[] {
  const [names, setNames] = useState<string[]>(() => cache?.map(b => b.name) ?? []);
  useEffect(() => {
    if (cache) return;
    pending ??= branchApi.list().then(list => (cache = list)).finally(() => { pending = null; });
    pending.then(list => setNames(list.map(b => b.name))).catch(() => { /* screens show their own load errors */ });
  }, []);
  return names;
}
