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

export const apiUrl = (process.env.EXPO_PUBLIC_API_URL ?? '').replace(/\/$/, '');
export const apiConfigured = apiUrl.startsWith('https://') || apiUrl.startsWith('http://localhost:');

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  if (!apiConfigured) throw new Error('API URL not configured');
  const response = await fetch(apiUrl + path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers || {}) },
  });
  if (!response.ok) {
    const error = await response.text().catch(() => '');
    throw new Error('API ' + response.status + ': ' + (error.slice(0, 160) || response.statusText));
  }
  return await response.json() as T;
}

export const enquiryApi = {
  list: () => request<Enquiry[]>('/api/v1/enquiries'),
  create: (data: NewEnquiry) => request<Enquiry>('/api/v1/enquiries', { method: 'POST', body: JSON.stringify(data) }),
  stage: (id: number, stage: Stage) => request<Enquiry>('/api/v1/enquiries/' + id + '/stage', { method: 'PATCH', body: JSON.stringify({ stage }) }),
  followUp: (id: number, followUpDate: string) => request<Enquiry>('/api/v1/enquiries/' + id + '/follow-up', { method: 'PATCH', body: JSON.stringify({ followUpDate }) }),
  activities: (id: number) => request<Activity[]>('/api/v1/enquiries/' + id + '/activities'),
  addActivity: (id: number, message: string) => request<Activity>('/api/v1/enquiries/' + id + '/activities', { method: 'POST', body: JSON.stringify({ message }) }),
};
