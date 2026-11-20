import { request } from './enquiries';

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'DELIVERED' | 'CANCELLED';
export type Booking = {
  id: number;
  enquiryId: number | null;
  vehicleId: number;
  branch: string;
  customerName: string;
  customerPhone: string;
  salesperson: string;
  status: BookingStatus;
  createdBy: string;
  confirmedBy: string | null;
  cancelledBy: string | null;
  cancellationReason: string | null;
  deliveredBy?: string | null;
  deliveredAt?: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};
export type BookingEvent = {
  id: number;
  bookingId: number;
  event: string;
  actorUsername: string;
  details: string | null;
  occurredAt: string | null;
};

export type BookingRequest =
  | { enquiryId: number; chassis: string }
  | { chassis: string; customerName: string; customerPhone: string };

export const bookingApi = {
  list: () => request<Booking[]>('/api/v1/bookings'),
  get: (id: number) => request<Booking>('/api/v1/bookings/' + id),
  events: (id: number) => request<BookingEvent[]>('/api/v1/bookings/' + id + '/events'),
  create: (payload: BookingRequest) => request<Booking>('/api/v1/bookings',
    { method: 'POST', body: JSON.stringify(payload) }),
  confirm: (id: number) => request<Booking>('/api/v1/bookings/' + id + '/confirm', { method: 'POST' }),
  cancel: (id: number, reason: string) => request<Booking>('/api/v1/bookings/' + id + '/cancel',
    { method: 'POST', body: JSON.stringify({ reason }) }),
};
