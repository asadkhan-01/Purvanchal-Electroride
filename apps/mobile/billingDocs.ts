import { Platform } from 'react-native';
import type { Invoice, Receipt } from './api/billing';
import { inr, modeTitle } from './api/billing';
import { rupeesInWords } from './billingWords';

export { rupeesInWords };

const esc = (s: unknown) => String(s ?? '').replace(/[&<>"']/g, c =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string));

const page = (title: string, body: string) => `<!doctype html><html><head><meta charset="utf-8">
<title>${esc(title)}</title><style>
body{font:13px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;color:#152F38;margin:32px;max-width:760px}
h1{font-size:20px;margin:0}h2{font-size:14px;margin:18px 0 6px}.muted{color:#647882}
table{width:100%;border-collapse:collapse;margin-top:8px}td,th{padding:6px 8px;border-bottom:1px solid #E3EAED;text-align:left}
td.n,th.n{text-align:right}.total td{font-weight:800;border-top:2px solid #152F38}
.head{display:flex;justify-content:space-between;gap:16px;border-bottom:2px solid #087D72;padding-bottom:12px}
.note{margin-top:24px;padding:10px;background:#FFF4DC;color:#805915;font-size:11px;border-radius:6px}
@media print{.note{break-inside:avoid}}</style></head><body>${body}
<script>window.onload=()=>window.print()</script></body></html>`;

const header = (doc: string, number: string, date: string, branch: string) => `<div class="head">
<div><h1>Purvanchal Electroride</h1><div class="muted">${esc(branch)} branch · Electric vehicle dealership</div>
<div class="muted">Dealer GSTIN: <i>not configured</i></div></div>
<div style="text-align:right"><h1>${esc(doc)}</h1><div>${esc(number)}</div><div class="muted">Date: ${esc(date)}</div></div></div>`;

const devNote = `<div class="note">Development preview. Not a valid tax document until the dealer GSTIN and
address are configured and the GST treatment has been reviewed by the dealership's accountant.</div>`;

export function invoiceHtml(inv: Invoice): string {
  const rows: [string, string][] = [['Vehicle price (ex-showroom, before GST)', inr(inv.vehiclePrice)],
    ['Less: dealer discount', '− ' + inr(inv.discount)], ['Accessories', inr(inv.accessories)], ['Other charges', inr(inv.otherCharges)]];
  const half = (inv.gstRate / 2).toFixed(2);
  const taxRows = inv.taxType === 'IGST'
    ? `<tr><td>IGST @ ${esc(inv.gstRate)}%</td><td class="n">${inr(inv.igst)}</td></tr>`
    : `<tr><td>CGST @ ${half}%</td><td class="n">${inr(inv.cgst)}</td></tr><tr><td>SGST @ ${half}%</td><td class="n">${inr(inv.sgst)}</td></tr>`;
  return page(inv.invoiceNumber, `${header('Tax Invoice', inv.invoiceNumber, inv.invoiceDate, inv.branch)}
<h2>Billed to</h2><div><b>${esc(inv.customerName)}</b> · ${esc(inv.customerPhone)}</div><div>${esc(inv.customerAddress)}</div>
${inv.customerGstin ? `<div>GSTIN: ${esc(inv.customerGstin)}</div>` : ''}
<h2>Vehicle</h2><table><tr><th>Model</th><th>Chassis</th><th>Motor no.</th><th>Battery no.</th></tr>
<tr><td>${esc(inv.model)} ${esc(inv.variant)}${inv.colour ? ' · ' + esc(inv.colour) : ''}</td><td>${esc(inv.chassis)}</td>
<td>${esc(inv.motorSerial)}</td><td>${esc(inv.batterySerial)}</td></tr></table>
<h2>Amount</h2><table>${rows.map(([l, v]) => `<tr><td>${esc(l)}</td><td class="n">${v}</td></tr>`).join('')}
<tr><td><b>Taxable value</b></td><td class="n"><b>${inr(inv.taxableValue)}</b></td></tr>${taxRows}
<tr><td>Round off</td><td class="n">${inr(inv.roundOff)}</td></tr>
<tr class="total"><td>Invoice total</td><td class="n">${inr(inv.grandTotal)}</td></tr></table>
<p><b>${esc(rupeesInWords(inv.grandTotal))}</b></p><p class="muted">Issued by ${esc(inv.issuedBy)}</p>${devNote}`);
}

export function receiptHtml(r: Receipt, customerName: string): string {
  return page(r.receiptNumber, `${header('Payment Receipt', r.receiptNumber, r.receivedOn, r.branch)}
<p>Received with thanks from <b>${esc(customerName)}</b> the sum of <b>${inr(r.amount)}</b>
(${esc(rupeesInWords(r.amount))}) by <b>${esc(modeTitle(r.mode))}</b>${r.reference ? ', reference ' + esc(r.reference) : ''}
${r.financeCompany ? ', disbursed by ' + esc(r.financeCompany) : ''}, against booking #${esc(r.bookingId)}.</p>
${r.status === 'VOIDED' ? `<p style="color:#B42318"><b>VOID</b> · ${esc(r.voidReason)}</p>` : ''}
${r.notes ? `<p class="muted">Note: ${esc(r.notes)}</p>` : ''}<p class="muted">Recorded by ${esc(r.recordedBy)}</p>${devNote}`);
}

/** Opens a printable document in a new tab (web). Returns false where printing isn't available. */
export function printDocument(html: string): boolean {
  if (Platform.OS !== 'web' || typeof window === 'undefined') return false;
  const w = window.open('', '_blank');
  if (!w) return false;
  w.document.open(); w.document.write(html); w.document.close();
  return true;
}
