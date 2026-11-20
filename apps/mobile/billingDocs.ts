import { Platform } from 'react-native';
import type { Invoice, Receipt } from './api/billing';
import { idTitle, inr, modeTitle } from './api/billing';
import { rupeesInWords } from './billingWords';

export { rupeesInWords };

const esc = (s: unknown) => String(s ?? '').replace(/[&<>"']/g, c =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string));

const page = (title: string, body: string) => `<!doctype html><html><head><meta charset="utf-8">
<title>${esc(title)}</title><style>
body{font:13px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;color:#152F38;margin:28px auto;max-width:780px;padding:0 16px}
.muted{color:#647882}.bar{display:flex;justify-content:space-between;font-weight:700;font-size:12px}
.tag{background:#152F38;color:#fff;padding:1px 10px;border-radius:5px}
.dup{text-align:center;font-weight:800;color:#B42318;border:1px dashed #B42318;padding:4px;margin:6px 0}
.brand{font-size:30px;letter-spacing:1px;text-align:center;margin:6px 0 0;font-weight:900}
.addr{text-align:center;border-bottom:2px solid #152F38;padding-bottom:8px;margin-bottom:8px}
.meta{display:flex;justify-content:space-between;margin:6px 0}
.f{display:flex;gap:8px;border-bottom:1px dotted #9AA9AF;padding:5px 0}.f .l{color:#647882;white-space:nowrap}.f .v{flex:1}
.row2{display:flex;gap:16px}.row2 .f{flex:1}
table{width:100%;border-collapse:collapse;margin-top:10px}td,th{padding:6px 8px;border:1px solid #9AA9AF;text-align:left;vertical-align:top}
td.n,th.n{text-align:right}th{background:#F1F5F6}
.bottom{display:flex;gap:12px;margin-top:-1px}.terms{flex:1;font-size:11px;border:1px solid #9AA9AF;padding:8px}
.terms ol{margin:4px 0 6px 18px;padding:0}.sum{width:46%;margin-top:0}.grand td{font-weight:900;font-size:14px;border-top:2px solid #152F38}
.sign{display:flex;justify-content:space-between;align-items:flex-end;margin-top:46px}.sign span:last-child{text-align:right}
.head{display:flex;justify-content:space-between;gap:16px;border-bottom:2px solid #087D72;padding-bottom:12px}
.note{margin-top:24px;padding:10px;background:#FFF4DC;color:#805915;font-size:11px;border-radius:6px}
@media print{.note{break-inside:avoid}}</style></head><body>${body}
<script>window.onload=()=>window.print()</script></body></html>`;

const dealerHeader = (doc: string, gstin: string | null, phone: string | null, address: string | null, branch: string) => `
<div class="bar"><span>${gstin ? 'GSTIN: ' + esc(gstin) : doc === 'Tax Invoice' ? 'GSTIN: <i>not configured</i>' : ''}</span><span class="tag">${esc(doc)}</span>
<span>${phone ? 'Mob.: ' + esc(phone) : ''}</span></div>
<h1 class="brand">PURVANCHAL ELECTRORIDE</h1>
<div class="addr">${address ? esc(address) : esc(branch) + ' branch'}</div>`;

export type DealerInfo = { dealerGstin: string | null; dealerPhone: string | null; dealerAddress: string | null };
const header = (doc: string, number: string, date: string, branch: string, d?: DealerInfo | null) =>
  `${dealerHeader(doc, d?.dealerGstin ?? null, d?.dealerPhone ?? null, d?.dealerAddress ?? null, branch)}
<div class="meta"><span>No. <b>${esc(number)}</b></span><span>Date: <b>${esc(date)}</b></span></div>`;


const field = (label: string, value: string) => `<div class="f"><span class="l">${esc(label)}</span><span class="v">${value}</span></div>`;
const dmy = (iso: string) => { const [y, m, d] = iso.split('-'); return `${d}/${m}/${y}`; };

/** Tax invoice laid out like the dealership's paper invoice book. */
export function invoiceHtml(inv: Invoice): string {
  const half = (inv.gstRate / 2).toFixed(2);
  const lines: [string, string, string, string][] = [
    [`Electric Scooter - <b>${esc(inv.model)}</b> ${esc(inv.variant)}`, '1', inr(inv.vehiclePrice - inv.discount), inr(inv.vehiclePrice - inv.discount)],
    ['Frame / Chassis No. - ' + esc(inv.chassis), '', '', ''],
    ['Motor No. - ' + esc(inv.motorSerial), '', '', ''],
    ...inv.batterySerials.split(',').map((b, i, all) =>
      [`Battery No.${all.length > 1 ? ' ' + (i + 1) : ''} - ${esc(b.trim())}`, '', '', ''] as [string, string, string, string]),
    ['Charger No. - ' + esc(inv.chargerSerial), '', '', ''],
    ['Controller No. - ' + esc(inv.controllerSerial), '', '', ''],
    ['Color - ' + esc(inv.colour ?? ''), '', '', ''],
    ['Accessories', '', '', inv.accessories ? inr(inv.accessories) : ''],
    ...(inv.otherCharges ? [['Other charges', '', '', inr(inv.otherCharges)] as [string, string, string, string]] : []),
  ];
  const tax = inv.taxType === 'IGST'
    ? `<tr><td colspan="3" class="n">IGST @ ${esc(inv.gstRate)}%</td><td class="n">${inr(inv.igst)}</td></tr>`
    : `<tr><td colspan="3" class="n">CGST @ ${half}%</td><td class="n">${inr(inv.cgst)}</td></tr>
       <tr><td colspan="3" class="n">SGST @ ${half}%</td><td class="n">${inr(inv.sgst)}</td></tr>`;
  const total = inv.taxableValue + inv.cgst + inv.sgst + inv.igst + inv.roundOff;
  const customer = esc(inv.customerName) + (inv.customerRelation ? ` ${esc(inv.customerRelation)} ${esc(inv.customerGuardianName)}` : '');
  return page(inv.invoiceNumber, `${dealerHeader('Tax Invoice', inv.dealerGstin, inv.dealerPhone, inv.dealerAddress, inv.branch)}
${inv.source === 'PAPER' ? `<div class="dup">DUPLICATE COPY — recorded from paper invoice${inv.paperBookNumber ? ' Book No. ' + esc(inv.paperBookNumber) + ',' : ''} Invoice No. ${esc(inv.paperInvoiceNumber)}</div>` : ''}
<div class="meta"><span>Invoice No. <b>${esc(inv.source === 'PAPER' ? (inv.paperBookNumber ? inv.paperBookNumber + '/' : '') + inv.paperInvoiceNumber : inv.invoiceNumber)}</b></span><span>Date: <b>${esc(dmy(inv.invoiceDate))}</b></span></div>
${field('Customer Name', customer)}${field('Mobile No.', esc(inv.customerPhone))}
${field('Address', esc(inv.customerAddress))}
${inv.customerGstin ? field('Customer GSTIN', esc(inv.customerGstin)) : ''}
<div class="row2">${field('Aadhaar No.', inv.aadhaarLast4 ? 'XXXX XXXX ' + esc(inv.aadhaarLast4) : '—')}
${field('Any ID', inv.idType ? esc(idTitle(inv.idType)) + ': ' + esc(inv.idNumber) : '—')}</div>
${field('Bank / Finance', inv.financier ? esc(inv.financier) : '—')}
<table class="goods"><tr><th style="width:36px">Sl.</th><th>Description of Goods</th><th class="n" style="width:50px">Qty.</th>
<th class="n" style="width:110px">Rate</th><th class="n" style="width:120px">Amount</th></tr>
${lines.map(([d, q, r, a], i) => `<tr><td>${i + 1}.</td><td>${d}</td><td class="n">${q}</td><td class="n">${r}</td><td class="n">${a}</td></tr>`).join('')}
</table>
<div class="bottom"><div class="terms"><b>Terms &amp; Conditions:</b><ol>
<li>Goods once sold will not be taken back.</li><li>All disputes subject to ${esc(inv.branch)} jurisdiction only.</li>
<li>Any damage or defect observed after purchase shall be rectified at your own cost.</li></ol>
<div>नोट- फुली-फटी बैट्री वारण्टी में वापस नहीं होगी।</div></div>
<table class="sum"><tr><td colspan="3" class="n">Taxable value</td><td class="n">${inr(inv.taxableValue)}</td></tr>${tax}
${inv.roundOff ? `<tr><td colspan="3" class="n">Round off</td><td class="n">${inv.roundOff < 0 ? '− ' + inr(-inv.roundOff) : inr(inv.roundOff)}</td></tr>` : ''}
<tr><td colspan="3" class="n"><b>Total</b></td><td class="n"><b>${inr(total)}</b></td></tr>
${inv.subsidy ? `<tr><td colspan="3" class="n">Less incentive / subsidy</td><td class="n">− ${inr(inv.subsidy)}</td></tr>` : ''}
<tr class="grand"><td colspan="3" class="n">GRAND TOTAL</td><td class="n">${inr(inv.grandTotal)}</td></tr></table></div>
${field('Rupees in Words', '<b>' + esc(rupeesInWords(inv.grandTotal)) + '</b>')}
<div class="sign"><span>Customer's Signature</span><span>For- <b>Purvanchal Electroride</b><br><small>Issued by ${esc(inv.issuedBy)}</small></span></div>`);
}

export function receiptHtml(r: Receipt, customerName: string, dealer?: DealerInfo | null): string {
  return page(r.receiptNumber, `${header('Payment Receipt', r.receiptNumber, dmy(r.receivedOn), r.branch, dealer)}
<p>Received with thanks from <b>${esc(customerName)}</b> the sum of <b>${inr(r.amount)}</b>
(${esc(rupeesInWords(r.amount))}) by <b>${esc(modeTitle(r.mode))}</b>${r.reference ? ', reference ' + esc(r.reference) : ''}
${r.financeCompany ? ', disbursed by ' + esc(r.financeCompany) : ''}, against booking #${esc(r.bookingId)}.</p>
${r.status === 'VOIDED' ? `<p style="color:#B42318"><b>VOID</b> · ${esc(r.voidReason)}</p>` : ''}
${r.notes ? `<p class="muted">Note: ${esc(r.notes)}</p>` : ''}<p class="muted">Recorded by ${esc(r.recordedBy)}</p>`);
}

/** Opens a printable document in a new tab (web). Returns false where printing isn't available. */
export function printDocument(html: string): boolean {
  if (Platform.OS !== 'web' || typeof window === 'undefined') return false;
  const w = window.open('', '_blank');
  if (!w) return false;
  w.document.open(); w.document.write(html); w.document.close();
  return true;
}
