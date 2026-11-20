export type Stage = 'New' | 'Contacted' | 'Interested' | 'Booked' | 'Lost';
export type Enquiry = {
  id: number;
  customerName: string;
  phone: string;
  branch: string;
  salesperson: string;
  source: string;
  interestedModel: string;
  stage: Stage;
  followUpDate: string | null;
  notes: string | null;
};
export type Activity = {
  id: number;
  enquiryId: number;
  message: string;
  createdAt: string | null;
};
export type NewEnquiry = Pick<Enquiry,
  'customerName' | 'phone' | 'branch' | 'salesperson' | 'source' | 'interestedModel' | 'notes'> &
  { followUpDate: string | null };

export type CurrentUser = {
  username: string;
  displayName: string;
  role: 'ADMIN' | 'BRANCH_MANAGER' | 'SALESPERSON';
  branch: string | null;
};

// Credentials are intentionally kept only in application memory and cleared on logout.
// Development-only Basic authentication; must be replaced for production use.
let authorization: string | null = null;
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}
function encodeAsciiBase64(value: string): string {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  let result = '';
  for (let i = 0; i < value.length; i += 3) {
    const a = value.charCodeAt(i), hasB = i + 1 < value.length, hasC = i + 2 < value.length;
    const b = hasB ? value.charCodeAt(i + 1) : 0;
    const c = hasC ? value.charCodeAt(i + 2) : 0;
    result += alphabet[a >> 2] + alphabet[((a & 3) << 4) | (b >> 4)]
      + (hasB ? alphabet[((b & 15) << 2) | (c >> 6)] : '=')
      + (hasC ? alphabet[c & 63] : '=');
  }
  return result;
}
export function setLoginCredentials(username: string, password: string) {
  const pair = username + ':' + password;
  if (!/^[\x20-\x7E]+$/.test(pair)) throw new Error('Use English letters, numbers and symbols only.');
  authorization = 'Basic ' + encodeAsciiBase64(pair);
}
export function clearLoginCredentials() { authorization = null; }

const rawApiUrl = process.env.EXPO_PUBLIC_API_URL ?? '';
export const apiUrl = rawApiUrl.replace(/\/$/, '');
// '/' means the API is served from the same origin as the web app (single-service deployment);
// a path such as '/backend' targets a same-origin development proxy (see scripts/dev-proxy.mjs).
export const apiConfigured = apiUrl.startsWith('https://') || apiUrl.startsWith('http://localhost:')
  || rawApiUrl === '/' || /^\/[A-Za-z0-9_-]+$/.test(apiUrl);

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  if (!apiConfigured) throw new Error('API URL not configured');
  const response = await fetch(apiUrl + path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(authorization ? { Authorization: authorization } : {}),
      ...(init?.headers || {}),
    },
  });
  if (response.status === 401) {
    clearLoginCredentials();
    onUnauthorized?.();
  }
  if (!response.ok) {
    const error = await response.text().catch(() => '');
    let message = error.slice(0, 160);
    try {
      const parsed = JSON.parse(error) as { detail?: string; title?: string };
      message = parsed.detail || parsed.title || message;
    } catch { /* not JSON */ }
    throw new Error('API ' + response.status + ': ' + (message || response.statusText));
  }
  return await response.json() as T;
}

export const enquiryApi = {
  me: () => request<CurrentUser>('/api/v1/auth/me'),
  list: () => request<Enquiry[]>('/api/v1/enquiries'),
  create: (data: NewEnquiry) => request<Enquiry>('/api/v1/enquiries', { method: 'POST', body: JSON.stringify(data) }),
  stage: (id: number, stage: Stage) => request<Enquiry>('/api/v1/enquiries/' + id + '/stage', { method: 'PATCH', body: JSON.stringify({ stage }) }),
  followUp: (id: number, followUpDate: string) => request<Enquiry>('/api/v1/enquiries/' + id + '/follow-up', { method: 'PATCH', body: JSON.stringify({ followUpDate }) }),
  activities: (id: number) => request<Activity[]>('/api/v1/enquiries/' + id + '/activities'),
  addActivity: (id: number, message: string) => request<Activity>('/api/v1/enquiries/' + id + '/activities', { method: 'POST', body: JSON.stringify({ message }) }),
};
