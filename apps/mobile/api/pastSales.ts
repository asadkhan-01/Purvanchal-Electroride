import { request } from './enquiries';
import type { Booking } from './bookings';
import type { Invoice, InvoiceRequest, PaymentMode, Receipt } from './billing';

export type PastPayment = { amount: string; mode: PaymentMode; reference?: string; financeCompany?: string; receivedOn: string };
export type PastSaleRequest = {
  branch: string; salesperson?: string; customerName: string; customerPhone: string; customer: InvoiceRequest;
  chassis: string;
  newVehicle?: { model: string; variant: string; colour?: string; motorSerial: string; batterySerials: string[]; chargerSerial: string; controllerSerial: string };
  paperBookNumber?: string; paperInvoiceNumber: string; invoiceDate: string; deliveredOn: string;
  vehiclePrice: string; discount: string; accessories: string; otherCharges: string; payments: PastPayment[];
};
export type PastSaleResult = { booking: Booking; invoice: Invoice; receipts: Receipt[] };

export const pastSaleApi = {
  record: (r: PastSaleRequest) => request<PastSaleResult>('/api/v1/past-sales', { method: 'POST', body: JSON.stringify(r) }),
};

export { previewTotals } from '../billingRules';
