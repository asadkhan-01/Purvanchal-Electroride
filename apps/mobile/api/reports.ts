import { request } from './enquiries';

export type SalesRow = {
  bookingId: number; invoiceNumber: string; source: 'APP' | 'PAPER'; invoiceDate: string; branch: string; salesperson: string;
  customerName: string; customerPhone: string; model: string; variant: string; colour: string | null; chassis: string;
  batterySerials: string; taxableValue: number; gst: number; subsidy: number; grandTotal: number; received: number;
  balance: number; paymentModes: string; financier: string | null; bookingStatus: string; deliveredOn: string | null;
};
export type SalesGroup = { name: string; count: number; grandTotal: number; received: number; balance: number };
export type SalesReport = {
  from: string; to: string; branch: string | null; model: string | null; salesperson: string | null;
  totals: { count: number; taxableValue: number; gst: number; subsidy: number; grandTotal: number; received: number; balance: number };
  byModel: SalesGroup[]; byBranch: SalesGroup[]; bySalesperson: SalesGroup[]; rows: SalesRow[];
  options: { branches: string[]; models: string[]; salespeople: string[] };
};
export type SalesFilter = { from: string; to: string; branch?: string; model?: string; salesperson?: string };

export const reportApi = {
  sales: (f: SalesFilter) => {
    const q = Object.entries(f).filter(([, v]) => v).map(([k, v]) => k + '=' + encodeURIComponent(v as string)).join('&');
    return request<SalesReport>('/api/v1/reports/sales?' + q);
  },
};
