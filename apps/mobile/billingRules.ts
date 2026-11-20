// Client-side checks for faster feedback only; the API enforces every rule.
type PaymentDraft = { amount: string; mode: string; reference: string; financeCompany: string; receivedOn: string };
type InvoiceDraft = {
  customerAddress: string; customerGstin: string; gstRate: string;
  customerRelation?: string; customerGuardianName?: string; idType?: string; idNumber?: string;
  aadhaarLast4?: string; subsidy?: string;
};

const money = /^(0|[1-9]\d{0,11})(\.\d{1,2})?$/;

export function validatePayment(p: PaymentDraft, balance: number): string | null {
  const amount = p.amount.trim();
  if (!money.test(amount) || Number(amount) <= 0) return 'Enter a positive amount with at most two decimals.';
  if (Number(amount) > balance + 1e-9) return 'Amount is more than the balance due.';
  if (p.mode !== 'CASH' && !p.reference.trim()) return 'Enter the payment reference (UTR, cheque or loan number).';
  if (p.mode === 'FINANCE' && !p.financeCompany.trim()) return 'Enter the finance company.';
  if (p.receivedOn.trim() && !/^\d{4}-\d{2}-\d{2}$/.test(p.receivedOn.trim())) return 'Received date must be YYYY-MM-DD.';
  return null;
}

export function validateInvoice(i: InvoiceDraft): string | null {
  if (!i.customerAddress.trim()) return 'Customer billing address is required.';
  const gstin = i.customerGstin.trim().toUpperCase();
  if (gstin && !/^[0-9]{2}[A-Z0-9]{13}$/.test(gstin)) return 'GSTIN must be 15 characters starting with the 2-digit state code.';
  const rate = i.gstRate.trim();
  if (!/^\d{1,2}(\.\d{1,2})?$/.test(rate) || Number(rate) > 28) return 'GST rate must be between 0 and 28.';
  if (i.customerRelation && !(i.customerGuardianName ?? '').trim()) return "Enter the father's / guardian's name.";
  if (i.idType && !(i.idNumber ?? '').trim()) return 'Enter the ID number for the selected ID type.';
  const aadhaar = (i.aadhaarLast4 ?? '').trim();
  if (aadhaar && !/^\d{4}$/.test(aadhaar)) return 'Enter only the last 4 digits of Aadhaar.';
  const subsidy = (i.subsidy ?? '').trim();
  if (subsidy && !money.test(subsidy)) return 'Subsidy must be an amount with at most two decimals.';
  return null;
}

/** Same rounding as the server: GST per component to paise, total to the nearest rupee, then less subsidy. */
export function previewTotals(taxable: number, rate: number, interState: boolean, subsidy: number) {
  const r2 = (n: number) => Math.round((n + Number.EPSILON) * 100) / 100;
  const igst = interState ? r2(taxable * rate / 100) : 0;
  const cgst = interState ? 0 : r2(taxable * rate / 200);
  const exact = taxable + 2 * cgst + igst;
  const rounded = Math.round(exact);
  return { cgst, sgst: cgst, igst, roundOff: r2(rounded - exact), total: rounded, grandTotal: r2(rounded - subsidy) };
}
