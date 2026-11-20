import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import type { CurrentUser } from './api/enquiries';
import type { Vehicle } from './api/inventory';
import { PaymentMode, idTypes, inr, paymentModes, relations } from './api/billing';
import { Salesperson, usersApi } from './api/users';
import { PastPayment, pastSaleApi, previewTotals } from './api/pastSales';
import { useBranches } from './api/branches';

const ink = '#18343D', teal = '#087D72', muted = '#637780', border = '#DCE6E9', red = '#B42318';
const money = /^(0|[1-9]\d{0,11})(\.\d{1,2})?$/;
const isoDate = /^\d{4}-\d{2}-\d{2}$/;
const todayIso = () => new Date(Date.now() + 5.5 * 3600e3).toISOString().slice(0, 10);

function Input({ label, value, change, hint, half }: { label: string; value: string; change: (v: string) => void; hint?: string; half?: boolean }) {
  return <View style={[s.field, half && s.half]}><Text style={s.label}>{label}</Text>
    <TextInput style={s.input} value={value} onChangeText={change} placeholder={hint} placeholderTextColor="#91A2A9" autoCorrect={false} /></View>;
}
function Chips({ items, value, choose }: { items: { id: string; title: string }[]; value: string; choose: (v: string) => void }) {
  return <View style={s.chips}>{items.map(i => <Pressable key={i.id} onPress={() => choose(i.id)} style={[s.chip, value === i.id && s.chipOn]}>
    <Text style={[s.chipText, value === i.id && s.chipTextOn]}>{i.title}</Text></Pressable>)}</View>;
}

type Pay = { amount: string; mode: PaymentMode; reference: string; financeCompany: string; receivedOn: string };
const blankPay = (): Pay => ({ amount: '', mode: 'UPI', reference: '', financeCompany: '', receivedOn: '' });

/** Manager/admin form to record a sale that already happened on a paper invoice, with its real dates. */
export default function PastSale({ user, stock, onDone, onCancel }: {
  user: CurrentUser; stock: Vehicle[]; onDone: (bookingId: number, message: string) => void; onCancel: () => void;
}) {
  const branches = useBranches();
  const [branch, setBranch] = useState(user.branch || '');
  useEffect(() => { if (!branch && branches.length) setBranch(branches[0]); }, [branches]);
  const [people, setPeople] = useState<Salesperson[]>([]);
  const [salesperson, setSalesperson] = useState(user.displayName);
  const [c, setC] = useState({ name: '', phone: '', relation: '', guardian: '', address: '', aadhaar: '', idType: '', idNumber: '', gstin: '', financier: '' });
  const [paper, setPaper] = useState({ book: '', number: '', invoiceDate: '', deliveredOn: '' });
  const [fromStock, setFromStock] = useState(false);
  const [chassis, setChassis] = useState('');
  const [v, setV] = useState({ model: '', variant: '', colour: '', motor: '', batteries: [''], charger: '', controller: '' });
  const [amt, setAmt] = useState({ inclusive: '', price: '', discount: '0', accessories: '0', other: '0', rate: '5', inter: false, subsidy: '0' });
  const [pays, setPays] = useState<Pay[]>([blankPay()]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { usersApi.salespeople().then(setPeople).catch(() => setPeople([])); }, []);
  const available = stock.filter(x => x.status === 'AVAILABLE' && x.branch === branch);
  const spOptions = [{ id: user.displayName, title: user.displayName + ' (me)' },
    ...people.filter(p => p.branch === branch).map(p => ({ id: p.displayName, title: p.displayName }))];

  const preview = useMemo(() => {
    const nums = [amt.price, amt.discount || '0', amt.accessories || '0', amt.other || '0'];
    if (!nums.every(n => money.test(n.trim())) || !money.test((amt.subsidy || '0').trim())) return null;
    const taxable = Number(amt.price) - Number(amt.discount || 0) + Number(amt.accessories || 0) + Number(amt.other || 0);
    if (!(taxable > 0)) return null;
    return { taxable, ...previewTotals(taxable, Number(amt.rate) || 0, amt.inter, Number(amt.subsidy || 0)) };
  }, [amt]);
  const paid = pays.reduce((sum, p) => sum + (money.test(p.amount.trim()) ? Number(p.amount) : 0), 0);

  const submit = async () => {
    const usedPays = pays.filter(p => p.amount.trim());
    const problem =
      !c.name.trim() ? 'Customer name is required.' : !/^\d{10}$/.test(c.phone.trim()) ? 'Enter a 10-digit mobile number.'
      : !c.address.trim() ? 'Customer address is required.' : c.relation && !c.guardian.trim() ? "Enter the father's / guardian's name."
      : c.aadhaar.trim() && !/^\d{4}$/.test(c.aadhaar.trim()) ? 'Aadhaar: enter only the last 4 digits.'
      : c.idType && !c.idNumber.trim() ? 'Enter the ID number.'
      : !paper.number.trim() ? 'Paper invoice number is required.'
      : !isoDate.test(paper.invoiceDate.trim()) ? 'Invoice date must be YYYY-MM-DD.' : !isoDate.test(paper.deliveredOn.trim()) ? 'Delivery date must be YYYY-MM-DD.'
      : paper.deliveredOn < paper.invoiceDate ? 'Delivery date cannot be before the invoice date.'
      : paper.invoiceDate > todayIso() || paper.deliveredOn > todayIso() ? 'Dates cannot be in the future.'
      : !chassis.trim() ? 'Choose or enter the chassis / frame number.'
      : !fromStock && (!v.model.trim() || !v.variant.trim() || !v.motor.trim() || !v.charger.trim() || !v.controller.trim() || !v.batteries.some(b => b.trim()))
        ? 'Enter model, variant, motor, battery, charger and controller numbers.'
      : !preview ? 'Enter valid amounts (vehicle price before GST is required).'
      : usedPays.some(p => !money.test(p.amount.trim()) || !isoDate.test(p.receivedOn.trim()) || p.receivedOn > todayIso()) ? 'Each payment needs an amount and a date (YYYY-MM-DD), not in the future.'
      : usedPays.some(p => p.mode !== 'CASH' && !p.reference.trim()) ? 'Non-cash payments need a reference (UTR, cheque or loan no.).'
      : usedPays.some(p => p.mode === 'FINANCE' && !p.financeCompany.trim()) ? 'Finance payments need the finance company.'
      : paid > (preview?.grandTotal ?? 0) + 1e-9 ? 'Payments are more than the invoice total.' : null;
    if (problem) { setError(problem); return; }
    setBusy(true); setError('');
    try {
      const payments: PastPayment[] = usedPays.map(p => ({ amount: p.amount.trim(), mode: p.mode, receivedOn: p.receivedOn.trim(),
        reference: p.reference.trim() || undefined, financeCompany: p.mode === 'FINANCE' ? p.financeCompany.trim() : undefined }));
      const res = await pastSaleApi.record({
        branch, salesperson, customerName: c.name.trim(), customerPhone: c.phone.trim(),
        customer: { customerAddress: c.address.trim(), customerGstin: c.gstin.trim() || undefined, gstRate: amt.rate.trim(), interState: amt.inter,
          customerRelation: c.relation || undefined, customerGuardianName: c.guardian.trim() || undefined,
          idType: c.idType || undefined, idNumber: c.idNumber.trim() || undefined, aadhaarLast4: c.aadhaar.trim() || undefined,
          financier: c.financier.trim() || payments.find(p => p.financeCompany)?.financeCompany, subsidy: amt.subsidy.trim() || undefined },
        chassis: chassis.trim(),
        newVehicle: fromStock ? undefined : { model: v.model.trim(), variant: v.variant.trim(), colour: v.colour.trim() || undefined,
          motorSerial: v.motor.trim(), batterySerials: v.batteries.map(b => b.trim()).filter(Boolean), chargerSerial: v.charger.trim(), controllerSerial: v.controller.trim() },
        paperBookNumber: paper.book.trim() || undefined, paperInvoiceNumber: paper.number.trim(),
        invoiceDate: paper.invoiceDate.trim(), deliveredOn: paper.deliveredOn.trim(),
        vehiclePrice: amt.price.trim(), discount: amt.discount.trim() || '0', accessories: amt.accessories.trim() || '0', otherCharges: amt.other.trim() || '0',
        payments,
      });
      onDone(res.booking.id, `Past sale recorded · ${res.invoice.invoiceNumber} · ${inr(res.invoice.grandTotal)}`);
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not record the sale'); }
    finally { setBusy(false); }
  };

  const setPay = (i: number, patch: Partial<Pay>) => setPays(pays.map((p, j) => j === i ? { ...p, ...patch } : p));
  return <View style={s.card}>
    <Text style={s.title}>Record past sale</Text>
    <Text style={s.info}>For sales already made on a paper invoice. It keeps the paper invoice number and the real dates, so revenue and
      payments count in the months they happened.</Text>
    {!!error && <Text style={s.error}>{error}</Text>}

    {user.role === 'ADMIN' && <><Text style={s.label}>Branch</Text>
      <Chips items={branches.map(b => ({ id: b, title: b }))} value={branch} choose={b => { setBranch(b); setChassis(''); }} /></>}
    <Text style={s.section}>Paper invoice</Text>
    <View style={s.row}>
      <Input half label="Book no." value={paper.book} change={book => setPaper({ ...paper, book })} hint="e.g. 16" />
      <Input half label="Invoice no. *" value={paper.number} change={number => setPaper({ ...paper, number })} />
      <Input half label="Invoice date * (YYYY-MM-DD)" value={paper.invoiceDate} change={invoiceDate => setPaper({ ...paper, invoiceDate })} />
      <Input half label="Delivered on * (YYYY-MM-DD)" value={paper.deliveredOn} change={deliveredOn => setPaper({ ...paper, deliveredOn })} hint="Same day if unsure" />
    </View>
    <Text style={s.label}>Salesperson</Text>
    <Chips items={spOptions} value={salesperson} choose={setSalesperson} />

    <Text style={s.section}>Customer</Text>
    <View style={s.row}>
      <Input half label="Customer name *" value={c.name} change={name => setC({ ...c, name })} />
      <Input half label="Mobile no. *" value={c.phone} change={phone => setC({ ...c, phone })} />
    </View>
    <Chips items={[{ id: '', title: 'No S/o' }, ...relations.map(r => ({ id: r as string, title: r }))]} value={c.relation} choose={relation => setC({ ...c, relation })} />
    {!!c.relation && <Input label="Father's / guardian's name *" value={c.guardian} change={guardian => setC({ ...c, guardian })} />}
    <Input label="Address *" value={c.address} change={address => setC({ ...c, address })} />
    <View style={s.row}>
      <Input half label="Aadhaar — last 4 digits" value={c.aadhaar} change={aadhaar => setC({ ...c, aadhaar })} hint="Full number is not stored" />
      <Input half label="Bank / finance" value={c.financier} change={financier => setC({ ...c, financier })} />
    </View>
    <Chips items={[{ id: '', title: 'No other ID' }, ...idTypes]} value={c.idType} choose={idType => setC({ ...c, idType })} />
    {!!c.idType && <Input label="ID number *" value={c.idNumber} change={idNumber => setC({ ...c, idNumber })} />}

    <Text style={s.section}>Vehicle</Text>
    <Chips items={[{ id: 'new', title: 'Not in app stock — enter details' }, { id: 'stock', title: `From ${branch} stock (${available.length})` }]}
      value={fromStock ? 'stock' : 'new'} choose={x => { setFromStock(x === 'stock'); setChassis(''); }} />
    {fromStock ? <Chips items={available.map(x => ({ id: x.chassis, title: `${x.chassis} · ${x.model}` }))} value={chassis} choose={setChassis} />
      : <>
        <View style={s.row}>
          <Input half label="Frame / chassis no. *" value={chassis} change={setChassis} />
          <Input half label="Model *" value={v.model} change={model => setV({ ...v, model })} hint="e.g. ZL-PRO" />
          <Input half label="Variant *" value={v.variant} change={variant => setV({ ...v, variant })} />
          <Input half label="Colour" value={v.colour} change={colour => setV({ ...v, colour })} />
          <Input half label="Motor no. *" value={v.motor} change={motor => setV({ ...v, motor })} />
          <Input half label="Charger no. *" value={v.charger} change={charger => setV({ ...v, charger })} />
          <Input half label="Controller no. *" value={v.controller} change={controller => setV({ ...v, controller })} />
        </View>
        {v.batteries.map((b, i) => <Input key={i} label={`Battery ${v.batteries.length > 1 ? i + 1 + ' ' : ''}no. *`} value={b}
          change={val => setV({ ...v, batteries: v.batteries.map((x, j) => j === i ? val : x) })} />)}
        <View style={s.row}>
          {v.batteries.length < 6 && <Pressable onPress={() => setV({ ...v, batteries: [...v.batteries, ''] })}><Text style={s.link}>+ Add battery</Text></Pressable>}
          {v.batteries.length > 1 && <Pressable onPress={() => setV({ ...v, batteries: v.batteries.slice(0, -1) })}><Text style={[s.link, { color: red }]}>Remove last battery</Text></Pressable>}
        </View>
      </>}

    <Text style={s.section}>Amounts</Text>
    <Input label="Total price incl. GST (helper)" value={amt.inclusive} hint="e.g. 50000 → price before GST 47619.05"
      change={inclusive => { const n = Number(inclusive); setAmt({ ...amt, inclusive,
        price: money.test(inclusive.trim()) && n > 0 ? (n / (1 + (Number(amt.rate) || 0) / 100)).toFixed(2) : amt.price }); }} />
    <View style={s.row}>
      <Input half label="Vehicle price before GST *" value={amt.price} change={price => setAmt({ ...amt, price })} />
      <Input half label="Discount" value={amt.discount} change={discount => setAmt({ ...amt, discount })} />
      <Input half label="Accessories" value={amt.accessories} change={accessories => setAmt({ ...amt, accessories })} />
      <Input half label="Other charges" value={amt.other} change={other => setAmt({ ...amt, other })} />
      <Input half label="GST rate %" value={amt.rate} change={rate => setAmt({ ...amt, rate })} />
      <Input half label="Less subsidy" value={amt.subsidy} change={subsidy => setAmt({ ...amt, subsidy })} />
    </View>
    <Chips items={[{ id: 'intra', title: 'Same state (CGST + SGST)' }, { id: 'inter', title: 'Other state (IGST)' }]}
      value={amt.inter ? 'inter' : 'intra'} choose={x => setAmt({ ...amt, inter: x === 'inter' })} />
    {preview && <View style={s.preview}>
      <Text style={s.info}>Taxable {inr(preview.taxable)} · {amt.inter ? 'IGST ' + inr(preview.igst) : 'CGST ' + inr(preview.cgst) + ' + SGST ' + inr(preview.sgst)} · round off {preview.roundOff < 0 ? '− ' + inr(-preview.roundOff) : inr(preview.roundOff)}</Text>
      <Text style={s.total}>Grand total {inr(preview.grandTotal)}</Text>
      <Text style={s.info}>Check this matches the paper invoice before saving.</Text>
    </View>}

    <Text style={s.section}>Payments received (with their real dates)</Text>
    {pays.map((p, i) => <View key={i} style={s.pay}>
      <View style={s.row}>
        <Input half label={`Amount ${i + 1}`} value={p.amount} change={amount => setPay(i, { amount })} />
        <Input half label="Date (YYYY-MM-DD)" value={p.receivedOn} change={receivedOn => setPay(i, { receivedOn })} />
      </View>
      <Chips items={paymentModes} value={p.mode} choose={mode => setPay(i, { mode: mode as PaymentMode })} />
      {p.mode !== 'CASH' && <Input label={p.mode === 'FINANCE' ? 'Loan / disbursement no.' : 'Reference (UTR / cheque no.)'} value={p.reference} change={reference => setPay(i, { reference })} />}
      {p.mode === 'FINANCE' && <Input label="Finance company" value={p.financeCompany} change={financeCompany => setPay(i, { financeCompany })} />}
    </View>)}
    <View style={s.row}>
      <Pressable onPress={() => setPays([...pays, blankPay()])}><Text style={s.link}>+ Add payment</Text></Pressable>
      {pays.length > 1 && <Pressable onPress={() => setPays(pays.slice(0, -1))}><Text style={[s.link, { color: red }]}>Remove last payment</Text></Pressable>}
    </View>
    {preview && <Text style={s.info}>Paid {inr(paid)} · {paid >= preview.grandTotal ? 'fully paid' : 'due ' + inr(preview.grandTotal - paid) + ' (will show in Pending dues)'}</Text>}

    <View style={s.row}>
      <Pressable disabled={busy} onPress={() => void submit()} style={s.primary}><Text style={s.primaryText}>{busy ? 'Saving…' : 'Save past sale'}</Text></Pressable>
      <Pressable onPress={onCancel} style={s.secondary}><Text style={s.secondaryText}>Cancel</Text></Pressable>
    </View>
  </View>;
}

const s = StyleSheet.create({
  card: { padding: 17, backgroundColor: '#FFF', borderWidth: 1, borderColor: border, borderRadius: 13, marginBottom: 12 },
  title: { fontSize: 18, color: ink, fontWeight: '800', marginBottom: 6 },
  section: { fontSize: 14, color: ink, fontWeight: '800', marginTop: 16, marginBottom: 6 },
  info: { color: muted, fontSize: 12, marginVertical: 3 },
  error: { color: red, padding: 10, backgroundColor: '#FDE8E8', borderRadius: 9, marginVertical: 8 },
  label: { fontSize: 12, color: ink, fontWeight: '700', marginTop: 6, marginBottom: 6 },
  field: { marginBottom: 8, width: '100%' },
  half: { width: '48%', flexGrow: 1 },
  input: { borderWidth: 1, borderColor: border, backgroundColor: '#FBFDFD', padding: 11, borderRadius: 9, color: ink, fontSize: 13 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, alignItems: 'center' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 8 },
  chip: { padding: 9, borderColor: border, borderWidth: 1, borderRadius: 9, backgroundColor: '#FFF' },
  chipOn: { backgroundColor: teal, borderColor: teal }, chipText: { color: ink, fontSize: 11 }, chipTextOn: { color: '#FFF' },
  preview: { padding: 12, borderRadius: 10, backgroundColor: '#F4F8F9', borderWidth: 1, borderColor: border, marginTop: 6 },
  total: { color: ink, fontSize: 16, fontWeight: '900', marginVertical: 3 },
  pay: { borderWidth: 1, borderColor: border, borderRadius: 10, padding: 10, marginBottom: 8 },
  link: { color: teal, fontSize: 12, fontWeight: '700', marginVertical: 6 },
  primary: { backgroundColor: teal, borderRadius: 9, padding: 13, marginTop: 12 },
  primaryText: { fontSize: 12, fontWeight: '800', color: '#FFF' },
  secondary: { backgroundColor: '#E4F3EE', borderRadius: 9, padding: 12, marginTop: 12 },
  secondaryText: { fontSize: 12, fontWeight: '800', color: teal },
});
