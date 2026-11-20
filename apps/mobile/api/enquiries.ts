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

/** API failure with a message that can be shown to users as-is. */
export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) { super(message); this.status = status; this.name = 'ApiError'; }
}

/** Turns an HTTP status and the server's problem detail into a plain sentence for users. */
export function friendlyError(status: number, detail: string | undefined): string {
  const d = (detail ?? '').trim();
  if (status === 401) return 'Incorrect username or password.';
  if (status >= 500) return 'Something went wrong on the server. Please try again in a moment.';
  if (status === 403) return d && d !== 'Forbidden' ? d + '.' : "You don't have permission to do this.";
  if (status === 404) return d && d !== 'Not Found' ? d + '.' : 'Not found.';
  if (status === 400 && (!d || d === 'Bad Request' || d.startsWith('Invalid request content') || d.startsWith('Failed to read request')))
    return 'Please check the details you entered.';
  return d ? (/[.!?]$/.test(d) ? d : d + '.') : 'The request could not be completed.';
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  if (!apiConfigured) throw new Error('The app is not connected to the server.');
  let response: Response;
  try {
    response = await fetch(apiUrl + path, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(authorization ? { Authorization: authorization } : {}),
        ...(init?.headers || {}),
      },
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your internet connection and try again.');
  }
  if (response.status === 401) {
    clearLoginCredentials();
    onUnauthorized?.();
  }
  if (!response.ok) {
    const body = await response.text().catch(() => '');
    let detail: string | undefined;
    try {
      const parsed = JSON.parse(body) as { detail?: string; title?: string };
      detail = parsed.detail || parsed.title;
    } catch { /* not JSON */ }
    throw new ApiError(response.status, friendlyError(response.status, detail));
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
