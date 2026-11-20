import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import type { CurrentUser } from './api/enquiries';
import type { Booking } from './api/bookings';
import { Account, PaymentMode, billingApi, idTitle, idTypes, inr, modeTitle, paymentModes, relations } from './api/billing';
import { invoiceHtml, printDocument, receiptHtml } from './billingDocs';
import { validatePayment, validateInvoice } from './billingRules';
import { parseDmy, toDmy, toDmyTime, todayDmy } from './dates';

const ink = '#18343D', teal = '#087D72', muted = '#637780', border = '#DCE6E9', red = '#B42318';

function Input({ label, value, change, hint }: { label: string; value: string; change: (v: string) => void; hint?: string }) {
  return <View style={s.field}><Text style={s.label}>{label}</Text>
    <TextInput style={s.input} value={value} onChangeText={change} placeholder={hint}
      placeholderTextColor="#91A2A9" autoCorrect={false} /></View>;
}
function Chips<T extends string>({ items, value, choose }: { items: { id: T; title: string }[]; value: T; choose: (v: T) => void }) {
  return <View style={s.chips}>{items.map(i => <Pressable key={i.id} onPress={() => choose(i.id)}
    style={[s.chip, value === i.id && s.chipOn]}><Text style={[s.chipText, value === i.id && s.chipTextOn]}>{i.title}</Text></Pressable>)}</View>;
}

/** Payments, receipts, invoice and delivery for one booking. Server enforces every rule; UI only hides controls. */
export default function BookingBilling({ user, booking, onChanged }: {
  user: CurrentUser; booking: Booking; onChanged: (message: string) => Promise<void> | void;
}) {
  const manager = user.role === 'ADMIN' || user.role === 'BRANCH_MANAGER';
  const [account, setAccount] = useState<Account | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [pay, setPay] = useState({ amount: '', mode: 'UPI' as PaymentMode, reference: '', financeCompany: '', receivedOn: todayDmy(), notes: '' });
  const [inv, setInv] = useState({ customerAddress: '', customerGstin: '', gstRate: '5', interState: false,
    customerRelation: '', customerGuardianName: '', idType: '', idNumber: '', aadhaarLast4: '', financier: '', subsidy: '' });
  const [voiding, setVoiding] = useState<number | null>(null);
  const [voidReason, setVoidReason] = useState('');

  const load = useCallback(async () => {
    try { setAccount(await billingApi.account(booking.id)); setError(''); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to load payments'); }
  }, [booking.id]);
  useEffect(() => { void load(); }, [load, booking.status]);

  const run = async (job: () => Promise<unknown>, message: string) => {
    if (busy) return;
    setBusy(true); setError('');
    try { await job(); await load(); await onChanged(message); }
    catch (e) { setError(e instanceof Error ? e.message : 'Action failed'); }
    finally { setBusy(false); }
  };

  if (!account) return <View><Text style={s.title}>Payments & invoice</Text>{error ? <Text style={s.error}>{error}</Text> : <Text style={s.info}>Loading…</Text>}</View>;

  const open = booking.status === 'PENDING' || booking.status === 'CONFIRMED';
  const balance = account.balance ?? 0;
  const canPay = (open || (booking.status === 'DELIVERED' && !!account.invoice)) && account.quotationApproved && balance > 0;
  const recordPayment = () => {
    const receivedIso = pay.receivedOn.trim() ? (parseDmy(pay.receivedOn) ?? 'invalid') : '';
    const problem = validatePayment({ ...pay, receivedOn: receivedIso }, balance);
    if (problem) { setError(problem); return; }
    void run(() => billingApi.pay(booking.id, {
      amount: pay.amount.trim(), mode: pay.mode, reference: pay.mode === 'CASH' ? undefined : pay.reference.trim(),
      financeCompany: pay.mode === 'FINANCE' ? pay.financeCompany.trim() : undefined,
      receivedOn: receivedIso || undefined, notes: pay.notes.trim() || undefined,
    }).then(r => { setPay(p => ({ ...p, amount: '', reference: '', notes: '' })); return r; }), 'Payment recorded');
  };
  const issueInvoice = () => {
    const problem = validateInvoice(inv);
    if (problem) { setError(problem); return; }
    void run(() => billingApi.issueInvoice(booking.id, {
      customerAddress: inv.customerAddress.trim(), customerGstin: inv.customerGstin.trim() || undefined,
      gstRate: inv.gstRate.trim(), interState: inv.interState,
      customerRelation: inv.customerRelation || undefined, customerGuardianName: inv.customerGuardianName.trim() || undefined,
      idType: inv.idType || undefined, idNumber: inv.idNumber.trim() || undefined,
      aadhaarLast4: inv.aadhaarLast4.trim() || undefined,
      financier: (inv.financier.trim() || account?.receipts.find(r => r.status === 'RECEIVED' && r.financeCompany)?.financeCompany) || undefined,
      subsidy: inv.subsidy.trim() || undefined,
    }), 'Invoice issued');
  };

  return <View>
    <Text style={s.title}>Payments & invoice</Text>
    {!!error && <Text style={s.error}>{error}</Text>}
    <View style={s.totals}>
      <View style={s.total}><Text style={s.totalLabel}>{account.estimated ? 'Payable (estimate)' : 'Invoice total'}</Text><Text style={s.totalValue}>{inr(account.payable)}</Text></View>
      <View style={s.total}><Text style={s.totalLabel}>Received</Text><Text style={s.totalValue}>{inr(account.received)}</Text></View>
      <View style={s.total}><Text style={s.totalLabel}>Balance due</Text><Text style={[s.totalValue, balance > 0 && { color: red }]}>{inr(account.balance)}</Text></View>
    </View>
    {account.estimated && account.payable != null && <Text style={s.info}>Estimate = approved quotation + 5% GST. The invoice sets the final amount.</Text>}
    {!account.quotationApproved && open && <Text style={s.info}>A manager must approve the quotation before payments can be collected.</Text>}

    {canPay && <View style={s.box}>
      <Text style={s.sub}>New payment</Text>
      <Input label="Amount (₹) *" value={pay.amount} change={amount => setPay({ ...pay, amount })} hint={'Up to ' + inr(balance)} />
      <Pressable onPress={() => setPay({ ...pay, amount: balance.toFixed(2) })}><Text style={s.link}>Use full balance ({inr(balance)})</Text></Pressable>
      <Text style={s.label}>Mode</Text>
      <Chips items={paymentModes} value={pay.mode} choose={mode => setPay({ ...pay, mode })} />
      {pay.mode !== 'CASH' && <Input label={pay.mode === 'FINANCE' ? 'Loan / disbursement no. *' : pay.mode === 'CHEQUE' ? 'Cheque no. *' : 'UTR / transaction ref *'}
        value={pay.reference} change={reference => setPay({ ...pay, reference })} />}
      {pay.mode === 'FINANCE' && <Input label="Finance company *" value={pay.financeCompany} change={financeCompany => setPay({ ...pay, financeCompany })} />}
      {pay.mode === 'CASH' && <Text style={s.info}>Total cash on one booking must stay below ₹2,00,000 (Income Tax Act s.269ST).</Text>}
      <Input label="Received on (DD/MM/YYYY)" value={pay.receivedOn} change={receivedOn => setPay({ ...pay, receivedOn })} />
      <Input label="Notes" value={pay.notes} change={notes => setPay({ ...pay, notes })} hint="e.g. booking advance" />
      <Pressable disabled={busy} style={s.primary} onPress={recordPayment}><Text style={s.primaryText}>Record payment</Text></Pressable>
    </View>}

    <Text style={s.sub}>Receipts ({account.receipts.length})</Text>
    {account.receipts.length === 0 && <Text style={s.info}>No payments yet.</Text>}
    {account.receipts.map(r => <View key={r.id} style={[s.row, r.status === 'VOIDED' && { opacity: 0.6 }]}>
      <View style={{ flex: 1 }}>
        <Text style={s.rowTitle}>{inr(r.amount)} · {modeTitle(r.mode)}{r.status === 'VOIDED' ? ' · VOIDED' : ''}</Text>
        <Text style={s.info}>{r.receiptNumber} · {toDmy(r.receivedOn)}{r.reference ? ' · ' + r.reference : ''}{r.financeCompany ? ' · ' + r.financeCompany : ''}</Text>
        {r.status === 'VOIDED' && <Text style={s.info}>Void: {r.voidReason} ({r.voidedBy})</Text>}
        {voiding === r.id && <>
          <Input label="Void reason *" value={voidReason} change={setVoidReason} hint="e.g. refunded, entered twice" />
          <View style={s.inline}>
            <Pressable disabled={busy} style={s.danger} onPress={() => {
              if (!voidReason.trim()) { setError('Void reason is required'); return; }
              void run(() => billingApi.voidReceipt(booking.id, r.id, voidReason.trim()), 'Receipt voided').then(() => { setVoiding(null); setVoidReason(''); });
            }}><Text style={s.primaryText}>Confirm void</Text></Pressable>
            <Pressable style={s.secondary} onPress={() => setVoiding(null)}><Text style={s.secondaryText}>Keep</Text></Pressable>
          </View>
        </>}
      </View>
      <View style={{ gap: 6 }}>
        <Pressable onPress={() => { if (!printDocument(receiptHtml(r, booking.customerName, account.invoice))) setError('Printing is available in the web app'); }}><Text style={s.link}>Print</Text></Pressable>
        {manager && r.status === 'RECEIVED' && open && voiding !== r.id && <Pressable onPress={() => { setVoiding(r.id); setVoidReason(''); }}><Text style={[s.link, { color: red }]}>Void</Text></Pressable>}
      </View>
    </View>)}

    <Text style={s.sub}>Tax invoice</Text>
    {account.invoice ? <View style={s.box}>
      <Text style={s.rowTitle}>{account.invoice.invoiceNumber} · {toDmy(account.invoice.invoiceDate)}</Text>
      <Text style={s.info}>Taxable value {inr(account.invoice.taxableValue)} · GST {account.invoice.gstRate}% {account.invoice.taxType === 'IGST'
        ? 'IGST ' + inr(account.invoice.igst) : 'CGST ' + inr(account.invoice.cgst) + ' + SGST ' + inr(account.invoice.sgst)}</Text>
      <Text style={s.info}>Round off {inr(account.invoice.roundOff)} · Total {inr(account.invoice.grandTotal)}</Text>
      <Text style={s.info}>{account.invoice.customerName}{account.invoice.customerRelation ? ' ' + account.invoice.customerRelation + ' ' + account.invoice.customerGuardianName : ''} · {account.invoice.customerAddress}{account.invoice.customerGstin ? ' · GSTIN ' + account.invoice.customerGstin : ''}</Text>
      {(account.invoice.aadhaarLast4 || account.invoice.idType || account.invoice.financier) && <Text style={s.info}>
        {[account.invoice.aadhaarLast4 && 'Aadhaar XXXX XXXX ' + account.invoice.aadhaarLast4,
          account.invoice.idType && idTitle(account.invoice.idType) + ' ' + account.invoice.idNumber,
          account.invoice.financier && 'Finance: ' + account.invoice.financier].filter(Boolean).join(' · ')}</Text>}
      {account.invoice.subsidy > 0 && <Text style={s.info}>Less subsidy {inr(account.invoice.subsidy)}</Text>}
      <Pressable onPress={() => { if (!printDocument(invoiceHtml(account.invoice!))) setError('Printing is available in the web app'); }}><Text style={s.link}>Print invoice</Text></Pressable>
    </View> : booking.status === 'CONFIRMED' && manager && account.quotationApproved ? <View style={s.box}>
      <Text style={s.label}>Relation (S/o, D/o…)</Text>
      <Chips items={[{ id: '', title: 'None' }, ...relations.map(r => ({ id: r as string, title: r }))]} value={inv.customerRelation}
        choose={customerRelation => setInv({ ...inv, customerRelation })} />
      {!!inv.customerRelation && <Input label="Father's / guardian's name *" value={inv.customerGuardianName}
        change={customerGuardianName => setInv({ ...inv, customerGuardianName })} />}
      <Input label="Customer billing address *" value={inv.customerAddress} change={customerAddress => setInv({ ...inv, customerAddress })} />
      <Input label="Aadhaar — last 4 digits only" value={inv.aadhaarLast4} change={aadhaarLast4 => setInv({ ...inv, aadhaarLast4 })}
        hint="Full Aadhaar numbers are not stored" />
      <Text style={s.label}>Other ID</Text>
      <Chips items={[{ id: '', title: 'None' }, ...idTypes]} value={inv.idType} choose={idType => setInv({ ...inv, idType })} />
      {!!inv.idType && <Input label="ID number *" value={inv.idNumber} change={idNumber => setInv({ ...inv, idNumber })} />}
      <Input label="Bank / finance company" value={inv.financier} change={financier => setInv({ ...inv, financier })}
        hint={account.receipts.find(r => r.financeCompany)?.financeCompany ?? 'e.g. finance company name'} />
      <Input label="Less: incentive / subsidy (₹)" value={inv.subsidy} change={subsidy => setInv({ ...inv, subsidy })} hint="0" />
      <Input label="Customer GSTIN (business buyers only)" value={inv.customerGstin} change={customerGstin => setInv({ ...inv, customerGstin })} />
      <Input label="GST rate (%)" value={inv.gstRate} change={gstRate => setInv({ ...inv, gstRate })} hint="5 for electric vehicles" />
      <Text style={s.label}>Place of supply</Text>
      <Chips items={[{ id: 'intra', title: 'Same state (CGST + SGST)' }, { id: 'inter', title: 'Other state (IGST)' }]}
        value={inv.interState ? 'inter' : 'intra'} choose={v => setInv({ ...inv, interState: v === 'inter' })} />
      <Text style={s.info}>The invoice is final once issued and locks the booking against cancellation.</Text>
      <Pressable disabled={busy} style={s.primary} onPress={issueInvoice}><Text style={s.primaryText}>Issue invoice</Text></Pressable>
    </View> : <Text style={s.info}>{booking.status === 'PENDING' ? 'A manager issues the invoice after confirming the booking.'
      : booking.status === 'CANCELLED' ? 'Cancelled booking.' : 'The branch manager issues the invoice.'}</Text>}

    {booking.status === 'DELIVERED' ? <Text style={s.success}>Delivered{booking.deliveredBy ? ' by ' + booking.deliveredBy : ''}{booking.deliveredAt ? ' · ' + toDmyTime(booking.deliveredAt) : ''}</Text>
      : booking.status === 'CONFIRMED' && manager && <>
        {account.invoice && balance === 0
          ? <Pressable disabled={busy} style={s.primary} onPress={() => void run(() => billingApi.deliver(booking.id), 'Vehicle delivered')}><Text style={s.primaryText}>Mark delivered</Text></Pressable>
          : <Text style={s.info}>Delivery unlocks once the invoice is issued and the balance is fully collected.</Text>}
      </>}
  </View>;
}

const s = StyleSheet.create({
  title: { fontSize: 16, color: ink, fontWeight: '800', marginTop: 14, marginBottom: 8 },
  sub: { fontSize: 13, color: ink, fontWeight: '800', marginTop: 14, marginBottom: 6 },
  info: { color: muted, fontSize: 12, marginVertical: 3 },
  error: { color: red, padding: 10, backgroundColor: '#FDE8E8', borderRadius: 9, marginVertical: 8 },
  success: { color: teal, fontSize: 13, fontWeight: '800', marginTop: 14 },
  totals: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  total: { flexGrow: 1, minWidth: 100, padding: 11, borderRadius: 10, backgroundColor: '#F4F8F9', borderWidth: 1, borderColor: border },
  totalLabel: { color: muted, fontSize: 11, fontWeight: '600' },
  totalValue: { color: ink, fontSize: 16, fontWeight: '900', marginTop: 3 },
  box: { padding: 13, borderWidth: 1, borderColor: border, borderRadius: 11, marginTop: 8, backgroundColor: '#FBFDFD' },
  row: { flexDirection: 'row', gap: 10, padding: 11, borderWidth: 1, borderColor: border, borderRadius: 10, marginBottom: 7 },
  rowTitle: { color: ink, fontSize: 13, fontWeight: '800' },
  label: { fontSize: 12, color: ink, fontWeight: '700', marginTop: 8, marginBottom: 6 },
  field: { marginBottom: 8 },
  input: { borderWidth: 1, borderColor: border, backgroundColor: '#FFF', padding: 11, borderRadius: 9, color: ink, fontSize: 13 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 8 },
  chip: { padding: 9, borderColor: border, borderWidth: 1, borderRadius: 9, backgroundColor: '#FFF' },
  chipOn: { backgroundColor: teal, borderColor: teal },
  chipText: { color: ink, fontSize: 11 }, chipTextOn: { color: '#FFF' },
  inline: { flexDirection: 'row', gap: 8 },
  primary: { backgroundColor: teal, borderRadius: 9, alignSelf: 'flex-start', padding: 12, marginTop: 10 },
  primaryText: { fontSize: 12, fontWeight: '800', color: '#FFF' },
  danger: { backgroundColor: red, borderRadius: 9, alignSelf: 'flex-start', padding: 11, marginTop: 6 },
  secondary: { backgroundColor: '#E4F3EE', borderRadius: 9, alignSelf: 'flex-start', padding: 11, marginTop: 6 },
  secondaryText: { fontSize: 12, fontWeight: '800', color: teal },
  link: { color: teal, fontSize: 12, fontWeight: '700', marginVertical: 4 },
});
