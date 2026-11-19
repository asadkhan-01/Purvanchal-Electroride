import { request } from './enquiries';

export type BookingQuotation = {
  id: number;
  bookingId: number;
  vehiclePrice: number;
  discount: number;
  accessories: number;
  otherCharges: number;
  subtotalExcludingTax: number;
  status: 'DRAFT' | 'APPROVED';
  createdBy: string;
  approvedBy: string | null;
  createdAt: string | null;
  approvedAt: string | null;
};
export type QuotationRequest = Pick<BookingQuotation,
  'vehiclePrice' | 'discount' | 'accessories' | 'otherCharges'>;

const path = (id: number) => '/api/v1/bookings/' + id + '/quotation';
export const quotationApi = {
  get: (bookingId: number) => request<BookingQuotation>(path(bookingId)),
  create: (bookingId: number, quote: QuotationRequest) => request<BookingQuotation>(
    path(bookingId), { method: 'POST', body: JSON.stringify(quote) }),
  approve: (bookingId: number) => request<BookingQuotation>(
    path(bookingId) + '/approve', { method: 'POST' }),
};
