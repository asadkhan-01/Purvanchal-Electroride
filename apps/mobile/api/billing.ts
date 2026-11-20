import { request } from './enquiries';
import type { Booking } from './bookings';

export type PaymentMode = 'CASH' | 'UPI' | 'CARD' | 'BANK_TRANSFER' | 'CHEQUE' | 'FINANCE';
export const paymentModes: { id: PaymentMode; title: string }[] = [
  { id: 'CASH', title: 'Cash' }, { id: 'UPI', title: 'UPI' }, { id: 'CARD', title: 'Card' },
  { id: 'BANK_TRANSFER', title: 'Bank transfer' }, { id: 'CHEQUE', title: 'Cheque' },
  { id: 'FINANCE', title: 'Finance / loan' },
];

export type Receipt = {
  id: number; bookingId: number; receiptNumber: string; branch: string; amount: number;
  mode: PaymentMode; reference: string | null; financeCompany: string | null; receivedOn: string;
  notes: string | null; status: 'RECEIVED' | 'VOIDED'; recordedBy: string;
  voidedBy: string | null; voidReason: string | null; createdAt: string | null;
};
export type Invoice = {
  id: number; bookingId: number; invoiceNumber: string; branch: string; invoiceDate: string;
  customerName: string; customerPhone: string; customerAddress: string; customerGstin: string | null;
  customerRelation: string | null; customerGuardianName: string | null; idType: string | null; idNumber: string | null;
  aadhaarLast4: string | null; financier: string | null; dealerGstin: string | null; dealerAddress: string | null; dealerPhone: string | null;
  chassis: string; model: string; variant: string; colour: string | null; motorSerial: string; batterySerials: string;
  chargerSerial: string; controllerSerial: string; subsidy: number;
  vehiclePrice: number; discount: number; accessories: number; otherCharges: number; taxableValue: number;
  gstRate: number; taxType: 'CGST_SGST' | 'IGST'; cgst: number; sgst: number; igst: number;
  roundOff: number; grandTotal: number; issuedBy: string;
};
export type Account = {
  bookingId: number; bookingStatus: string; quotationApproved: boolean; estimated: boolean;
  payable: number | null; received: number; balance: number | null;
  invoice: Invoice | null; receipts: Receipt[];
};
export type PaymentRequest = {
  amount: string; mode: PaymentMode; reference?: string; financeCompany?: string; receivedOn?: string; notes?: string;
};
export type InvoiceRequest = {
  customerAddress: string; customerGstin?: string; gstRate?: string; interState: boolean;
  customerRelation?: string; customerGuardianName?: string; idType?: string; idNumber?: string;
  aadhaarLast4?: string; financier?: string; subsidy?: string;
};
export const relations = ['S/o', 'D/o', 'W/o', 'C/o'] as const;
export const idTypes = [{ id: 'PAN', title: 'PAN' }, { id: 'DRIVING_LICENCE', title: 'Driving licence' },
  { id: 'VOTER_ID', title: 'Voter ID' }, { id: 'PASSPORT', title: 'Passport' }, { id: 'OTHER', title: 'Other' }];
export const idTitle = (t: string | null) => idTypes.find(x => x.id === t)?.title ?? t ?? '';

export type Due = {
  bookingId: number; customerName: string; branch: string; salesperson: string; bookingStatus: string;
  invoiceNumber: string | null; estimated: boolean; payable: number; received: number; balance: number;
};
export type ReceiptRow = {
  id: number; bookingId: number; receiptNumber: string; customerName: string; branch: string;
  amount: number; mode: PaymentMode; reference: string | null; receivedOn: string; status: string;
};
export type FinanceSummary = {
  month: string; invoicedThisMonth: number; invoicesThisMonth: number; collectedThisMonth: number;
  receiptsThisMonth: number; collectedByMode: Record<string, number>; outstandingDues: number;
  bookingsWithDues: number; advancesHeld: number; deliveredThisMonth: number;
  dues: Due[]; recentReceipts: ReceiptRow[];
};

const base = (id: number) => '/api/v1/bookings/' + id;
// Amounts are sent as strings so the server receives exact decimals.
export const billingApi = {
  account: (id: number) => request<Account>(base(id) + '/account'),
  pay: (id: number, p: PaymentRequest) => request<Receipt>(base(id) + '/payments', { method: 'POST', body: JSON.stringify(p) }),
  voidReceipt: (id: number, receiptId: number, reason: string) => request<Receipt>(
    base(id) + '/payments/' + receiptId + '/void', { method: 'POST', body: JSON.stringify({ reason }) }),
  issueInvoice: (id: number, r: InvoiceRequest) => request<Invoice>(base(id) + '/invoice', { method: 'POST', body: JSON.stringify(r) }),
  deliver: (id: number) => request<Booking>(base(id) + '/deliver', { method: 'POST' }),
  summary: (month?: string) => request<FinanceSummary>('/api/v1/finance/summary' + (month ? '?month=' + month : '')),
};

export const inr = (n: number | null | undefined) => n == null ? '—'
  : '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const modeTitle = (m: string) => paymentModes.find(x => x.id === m)?.title ?? m;
