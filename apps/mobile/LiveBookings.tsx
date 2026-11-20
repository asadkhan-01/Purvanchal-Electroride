import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { CurrentUser, Enquiry, enquiryApi } from './api/enquiries';
import { Vehicle, inventoryApi } from './api/inventory';
import { Booking, BookingEvent, bookingApi } from './api/bookings';
import { bookingPayload } from './bookingRules';
import { BookingQuotation, quotationApi } from './api/quotations';
import BookingBilling from './BookingBilling';
import PastSale from './PastSale';

type Props = { user: CurrentUser; enquiryId?: number | null; onClearEnquiry?: () => void };
const ink = '#18343D', teal = '#087D72', muted = '#637780', border = '#DCE6E9';
const salesNote = 'Quotation → confirmation → payments → GST invoice → delivery. GST treatment needs accountant review before real use'

function Input({ label, value, change, hint }: {
  label: string; value: string; change: (value: string) => void; hint?: string;
}) {
  return <View style={s.field}><Text style={s.label}>{label}</Text>
    <TextInput style={s.input} value={value} onChangeText={change} placeholder={hint}
      placeholderTextColor="#91A2A9" autoCorrect={false} /></View>;
}
function Select({ options, selected, onChange }: {
  options: { id: string; title: string }[]; selected: string; onChange: (value: string) => void;
}) {
  return <View style={s.chips}>{options.map(opt => <Pressable key={opt.id}
    style={[s.chip, selected === opt.id && s.chipActive]} onPress={() => onChange(opt.id)}>
    <Text style={[s.chipText, selected === opt.id && s.chipTextActive]}>{opt.title}</Text>
  </Pressable>)}</View>;
}

export default function LiveBookings({ user, enquiryId, onClearEnquiry }: Props) {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [enquiries, setEnquiries] = useState<Enquiry[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [mode, setMode] = useState<'enquiry' | 'direct'>(enquiryId != null ? 'enquiry' : 'direct');
  const [selectedEnquiry, setSelectedEnquiry] = useState(enquiryId ? String(enquiryId) : '');
  const [chassis, setChassis] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerPhone, setCustomerPhone] = useState('');
  const [selectedBooking, setSelectedBooking] = useState<number | null>(null);
  const [events, setEvents] = useState<BookingEvent[]>([]);
  const [reason, setReason] = useState('');
  const [quotation, setQuotation] = useState<BookingQuotation | null>(null);
  const [inclusive, setInclusive] = useState('');
  const [recordingPast, setRecordingPast] = useState(false);
  const [quoteFields, setQuoteFields] = useState({
    vehiclePrice: '', discount: '0', accessories: '0', otherCharges: '0',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const reload = useCallback(async () => {
    setBusy(true);
    try {
      const [sales, crm, stock] = await Promise.all([
        bookingApi.list(), enquiryApi.list(), inventoryApi.list(),
      ]);
      setBookings(sales); setEnquiries(crm); setVehicles(stock); setError('');
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to load bookings'); }
    finally { setBusy(false); }
  }, []);

  useEffect(() => { void reload(); }, [reload, user.username]);
  useEffect(() => {
    if (enquiryId != null) {
      setMode('enquiry'); setSelectedEnquiry(String(enquiryId)); setSelectedBooking(null);
    }
  }, [enquiryId]);

  const active = bookings.find(b => b.id === selectedBooking);
  const enquiry = enquiries.find(e => String(e.id) === selectedEnquiry);
  const available = useMemo(() => vehicles.filter(v => {
    if (v.status !== 'AVAILABLE') return false;
    if (mode === 'enquiry') return enquiry != null && v.branch === enquiry.branch;
    return user.role === 'ADMIN' || v.branch === user.branch;
  }), [vehicles, mode, enquiry, user]);
  const managersOnly = user.role === 'ADMIN' || user.role === 'BRANCH_MANAGER';

  const readQuotation = async (id: number) => {
    try { setQuotation(await quotationApi.get(id)); }
    catch (e) {
      if (e instanceof Error && e.message.startsWith('API 404:')) setQuotation(null);
      else setError(e instanceof Error ? e.message : 'Unable to load quotation');
    }
  };

  const createQuotation = async (bookingId: number) => {
    if (busy) return;
    const amounts = Object.values(quoteFields);
    if (amounts.some(value => !/^(0|[1-9]\d*)(\.\d{1,2})?$/.test(value.trim()))) {
      setError('Enter non-negative amounts with at most two decimal places.'); return;
    }
    setBusy(true); setError(''); setNotice('');
    try {
      const created = await quotationApi.create(bookingId, {
        vehiclePrice: Number(quoteFields.vehiclePrice),
        discount: Number(quoteFields.discount),
        accessories: Number(quoteFields.accessories),
        otherCharges: Number(quoteFields.otherCharges),
      });
      setQuotation(created);
      setEvents(await bookingApi.events(bookingId));
      setNotice('Draft quotation saved');
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to create quotation'); }
    finally { setBusy(false); }
  };

  const approveQuotation = async (bookingId: number) => {
    if (busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const approved = await quotationApi.approve(bookingId);
      setQuotation(approved);
      setEvents(await bookingApi.events(bookingId));
      setNotice('Quotation approved');
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to approve quotation'); }
    finally { setBusy(false); }
  };

  const readEvents = async (id: number) => {
    try { setEvents(await bookingApi.events(id)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to read booking history'); }
  };

  const perform = async (action: () => Promise<Booking>, label: string) => {
    if (busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const changed = await action();
      const [newBookings, newEnquiries, newVehicles] = await Promise.all([
        bookingApi.list(), enquiryApi.list(), inventoryApi.list(),
      ]);
      setBookings(newBookings); setEnquiries(newEnquiries); setVehicles(newVehicles);
      setSelectedBooking(changed.id); setEvents(await bookingApi.events(changed.id));
      await readQuotation(changed.id);
      setNotice(label); setReason('');
      setChassis('');
      onClearEnquiry?.();
    } catch (e) { setError(e instanceof Error ? e.message : 'Booking action failed'); }
    finally { setBusy(false); }
  };

  const create = () => {
    try {
      const payload = bookingPayload(
        mode, chassis, enquiry?.id ?? null, customerName, customerPhone,
      );
      void perform(() => bookingApi.create(payload),
        mode === 'enquiry' ? 'Booking created from enquiry' : 'Direct booking created');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Invalid booking details');
    }
  };

  const viewBooking = (id: number) => {
    setSelectedBooking(id); setEvents([]); setQuotation(null); setError('');
    void readEvents(id); void readQuotation(id);
  };

  return <View style={s.root}>
    <View style={s.banner}><Text style={s.bannerText}>DEVELOPMENT SALES · {salesNote}. Use test customer details only.</Text></View>
    <Text style={s.heading}>Sales & Bookings</Text>
    <Text style={s.sub}>{user.displayName} · {user.role} · {user.branch || 'All branches'}</Text>
    <View style={s.actions}><Pressable onPress={() => void reload()} disabled={busy} style={s.secondary}>
      <Text style={s.secondaryText}>Refresh</Text></Pressable>
      {selectedBooking != null && <Pressable onPress={() => { setSelectedBooking(null); setEvents([]); setQuotation(null); }} style={s.secondary}>
        <Text style={s.secondaryText}>New booking / list</Text></Pressable>}
    </View>
    {busy && <ActivityIndicator color={teal} />}
    {!!error && <Text style={s.error}>{error}</Text>}
    {!!notice && <Text style={s.success}>{notice}</Text>}
    {recordingPast ? <PastSale user={user} stock={vehicles} onCancel={() => setRecordingPast(false)}
      onDone={(id, message) => { setRecordingPast(false); void reload().then(() => { viewBooking(id); setNotice(message); }); }} />
    : active ? <View style={s.card}>
      <Text style={s.title}>Booking #{active.id} · {active.status}{active.recordedFromPaper ? ' · PAPER INVOICE' : ''}</Text>
      <Text style={s.info}>Customer: {active.customerName} · {active.customerPhone}</Text>
      <Text style={s.info}>Source: {active.enquiryId == null ? 'Direct booking' : 'Enquiry #' + active.enquiryId}</Text>
      <Text style={s.info}>Branch: {active.branch} · Salesperson: {active.salesperson}</Text>
      <Text style={s.info}>Vehicle: {vehicles.find(v => v.id === active.vehicleId)?.chassis ?? '#' + active.vehicleId}</Text>
      <Text style={s.title}>Quotation · tax-exclusive estimate</Text>
      {quotation ? <>
        <Text style={s.info}>Status: {quotation.status} · Created by {quotation.createdBy}</Text>
        <Text style={s.info}>Vehicle price: ₹{quotation.vehiclePrice.toFixed(2)}</Text>
        <Text style={s.info}>Dealer discount: ₹{quotation.discount.toFixed(2)}</Text>
        <Text style={s.info}>Accessories: ₹{quotation.accessories.toFixed(2)}</Text>
        <Text style={s.info}>Other charges: ₹{quotation.otherCharges.toFixed(2)}</Text>
        <Text style={s.title}>Subtotal excluding GST: ₹{quotation.subtotalExcludingTax.toFixed(2)}</Text>
        {quotation.status === 'DRAFT' && active.status === 'PENDING' && managersOnly &&
          <Pressable disabled={busy} style={s.primary} onPress={() => void approveQuotation(active.id)}>
            <Text style={s.primaryText}>Approve quotation</Text>
          </Pressable>}
      </> : active.status === 'PENDING' ? <>
        <Text style={s.info}>Enter amounts in INR before GST. GST is added on the invoice; insurance, registration and subsidy are not included.</Text>
        <Input label="Price including 5% GST (optional helper)" value={inclusive}
          change={value => { setInclusive(value); const n = Number(value);
            if (/^\d+(\.\d{1,2})?$/.test(value.trim()) && n > 0) setQuoteFields(prev => ({ ...prev, vehiclePrice: (n / 1.05).toFixed(2) })); }}
          hint="e.g. 50000 → vehicle price 47619.05" />
        <Input label="Vehicle price (₹) *" value={quoteFields.vehiclePrice}
          change={vehiclePrice => setQuoteFields(prev => ({ ...prev, vehiclePrice }))} />
        <Input label="Dealer discount (₹)" value={quoteFields.discount}
          change={discount => setQuoteFields(prev => ({ ...prev, discount }))} />
        <Input label="Accessories (₹)" value={quoteFields.accessories}
          change={accessories => setQuoteFields(prev => ({ ...prev, accessories }))} />
        <Input label="Other charges (₹)" value={quoteFields.otherCharges}
          change={otherCharges => setQuoteFields(prev => ({ ...prev, otherCharges }))} />
        <Pressable disabled={busy} style={s.primary} onPress={() => void createQuotation(active.id)}>
          <Text style={s.primaryText}>Save draft quotation</Text>
        </Pressable>
      </> : <Text style={s.info}>No quotation was recorded before this booking progressed.</Text>}
      {active.status === 'PENDING' && managersOnly && <Pressable style={s.primary}
        disabled={busy} onPress={() => void perform(() => bookingApi.confirm(active.id), 'Booking confirmed')}>
        <Text style={s.primaryText}>Confirm booking</Text></Pressable>}
      <BookingBilling user={user} booking={active} onChanged={async message => {
        const [newBookings, newVehicles] = await Promise.all([bookingApi.list(), inventoryApi.list()]);
        setBookings(newBookings); setVehicles(newVehicles);
        await readEvents(active.id); setNotice(message);
      }} />
      {(active.status === 'PENDING' || active.status === 'CONFIRMED') && managersOnly && <>
        <Input label="Cancellation reason *" value={reason} change={setReason}
          hint="Reason for releasing the vehicle" />
        <Pressable style={s.secondary} disabled={busy} onPress={() => {
          if (!reason.trim()) { setError('Cancellation reason is required'); return; }
          void perform(() => bookingApi.cancel(active.id, reason.trim()), 'Booking cancelled and stock released');
        }}><Text style={s.secondaryText}>Cancel booking</Text></Pressable>
      </>}
      <Text style={s.title}>Booking history</Text>
      {events.map(e => <Text key={e.id} style={s.info}>
        • {e.event} · {e.actorUsername}{e.occurredAt ? ' · ' + new Date(e.occurredAt).toLocaleString() : ''}
      </Text>)}
    </View> : <>
      <View style={s.card}>
        <Text style={s.title}>Create booking</Text>
        <Select options={[{ id: 'direct', title: 'Direct booking' }, { id: 'enquiry', title: 'From enquiry' }]}
          selected={mode} onChange={next => { setMode(next as 'direct' | 'enquiry'); setChassis(''); }} />
        {mode === 'enquiry' ? <>
          <Text style={s.label}>Select enquiry</Text>
          <Select options={enquiries.filter(e => e.stage !== 'Booked' && e.stage !== 'Lost')
            .map(e => ({ id: String(e.id), title: e.customerName + ' · #' + e.id }))}
            selected={selectedEnquiry} onChange={id => { setSelectedEnquiry(id); setChassis(''); }}/>
          {enquiry && <Text style={s.info}>Customer: {enquiry.customerName} · {enquiry.phone} · {enquiry.branch}</Text>}
        </> : <>
          <Input label="Customer name *" value={customerName} change={setCustomerName} />
          <Input label="Customer mobile *" value={customerPhone} change={setCustomerPhone} hint="10-digit mobile" />
        </>}
        <Text style={s.label}>Select available vehicle</Text>
        <Select options={available.map(v => ({
          id: v.chassis, title: v.chassis + ' · ' + v.model + ' · ' + v.branch,
        }))} selected={chassis} onChange={setChassis} />
        {!available.length && <Text style={s.sub}>No available vehicles for this booking scope.</Text>}
        <Pressable disabled={busy || !chassis} style={s.primary} onPress={create}>
          <Text style={s.primaryText}>Create pending booking</Text>
        </Pressable>
      </View>
      {managersOnly && <Pressable style={s.secondary} onPress={() => { setSelectedBooking(null); setRecordingPast(true); }}>
        <Text style={s.secondaryText}>+ Record past sale (paper invoice)</Text></Pressable>}
      <Text style={s.title}>Existing bookings</Text>
      {bookings.map(b => <Pressable key={b.id} style={s.card} onPress={() => viewBooking(b.id)}>
        <Text style={s.title}>#{b.id} · {b.customerName} · {b.status}{b.recordedFromPaper ? ' · paper' : ''}</Text>
        <Text style={s.info}>{b.branch} · {b.salesperson} · {b.enquiryId ? 'Enquiry #' + b.enquiryId : 'Direct'}</Text>
        <Text style={s.link}>View booking →</Text>
      </Pressable>)}
      {!bookings.length && <Text style={s.sub}>No bookings found.</Text>}
    </>}
  </View>;
}

const s = StyleSheet.create({
  root: { paddingBottom: 30 }, heading: { color: ink, fontWeight: '800', fontSize: 23 },
  sub: { fontSize: 12, color: muted, marginVertical: 8 },
  banner: { padding: 12, borderRadius: 9, backgroundColor: '#FFF1D8', marginBottom: 14 },
  bannerText: { fontSize: 11, color: '#805915', fontWeight: '700' },
  card: { padding: 17, backgroundColor: '#FFF', borderWidth: 1, borderColor: border, borderRadius: 13, marginBottom: 12 },
  title: { fontSize: 16, color: ink, fontWeight: '800', marginVertical: 11 },
  label: { fontSize: 12, color: ink, fontWeight: '700', marginTop: 12, marginBottom: 8 },
  field: { marginBottom: 9 }, input: { borderWidth: 1, borderColor: border, backgroundColor: '#FBFDFD',
    padding: 12, borderRadius: 9, color: ink, fontSize: 13 },
  info: { color: muted, fontSize: 12, marginVertical: 5 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 12 },
  chip: { padding: 9, borderColor: border, borderWidth: 1, borderRadius: 9, backgroundColor: '#FFF' },
  chipActive: { backgroundColor: teal, borderColor: teal },
  chipText: { color: ink, fontSize: 11 }, chipTextActive: { color: '#FFF' },
  actions: { flexDirection: 'row', gap: 9, marginVertical: 10, flexWrap: 'wrap' },
  primary: { backgroundColor: teal, borderRadius: 9, alignSelf: 'flex-start', padding: 13, marginTop: 12 },
  primaryText: { fontSize: 12, fontWeight: '800', color: '#FFF' },
  secondary: { backgroundColor: '#E4F3EE', borderRadius: 9, alignSelf: 'flex-start', padding: 12, marginTop: 8 },
  secondaryText: { fontSize: 12, fontWeight: '800', color: teal },
  error: { color: '#B42318', padding: 11, backgroundColor: '#FDE8E8', borderRadius: 9, marginVertical: 9 },
  success: { color: teal, fontSize: 12, fontWeight: '700', marginVertical: 9 },
  link: { color: teal, fontSize: 12, fontWeight: '700', marginTop: 8 },
});
