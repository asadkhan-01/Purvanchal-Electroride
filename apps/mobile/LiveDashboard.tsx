import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { CurrentUser, Enquiry, enquiryApi } from './api/enquiries';
import { Vehicle, inventoryApi } from './api/inventory';
import { Booking, bookingApi } from './api/bookings';
import { FinanceSummary, billingApi, inr } from './api/billing';

const ink = '#152F38', teal = '#087D72', muted = '#647882', line = '#E3EAED';
const stageColor: Record<string, string> = { New: '#3667B3', Contacted: '#A96510', Interested: teal, Booked: '#2E7D32', Lost: '#9A3B3B' };

type Data = { enquiries: Enquiry[]; vehicles: Vehicle[]; bookings: Booking[]; finance: FinanceSummary };

/** Dashboard built only from records the signed-in user is allowed to see. */
export default function LiveDashboard({ user, wide, onOpen }: {
  user: CurrentUser; wide: boolean; onOpen: (section: 'Enquiries' | 'Inventory' | 'Sales' | 'Finance') => void;
}) {
  const [data, setData] = useState<Data | null>(null);
  const [error, setError] = useState('');
  const load = useCallback(async () => {
    setError('');
    try {
      const [enquiries, vehicles, bookings, finance] = await Promise.all([
        enquiryApi.list(), inventoryApi.list(), bookingApi.list(), billingApi.summary()]);
      setData({ enquiries, vehicles, bookings, finance });
    } catch (e) { setError(e instanceof Error ? e.message : 'Unable to load dashboard'); }
  }, []);
  useEffect(() => { void load(); }, [user.username, load]);

  if (error) return <View style={s.card}><Text style={s.error}>{error}</Text>
    <Pressable onPress={() => void load()}><Text style={s.link}>Retry</Text></Pressable></View>;
  if (!data) return <ActivityIndicator color={teal} />;

  const today = new Date().toISOString().slice(0, 10);
  const open = data.enquiries.filter(e => e.stage !== 'Booked' && e.stage !== 'Lost');
  const due = open.filter(e => e.followUpDate && e.followUpDate <= today);
  const available = data.vehicles.filter(v => v.status === 'AVAILABLE');
  const pending = data.bookings.filter(b => b.status === 'PENDING');
  const confirmed = data.bookings.filter(b => b.status === 'CONFIRMED');
  const metrics = [
    { label: 'Open enquiries', value: open.length, note: `${due.length} follow-up${due.length === 1 ? '' : 's'} due`, go: 'Enquiries' as const },
    { label: 'Available stock', value: available.length, note: `${data.vehicles.length} vehicles visible`, go: 'Inventory' as const },
    { label: 'Pending bookings', value: pending.length, note: 'Awaiting manager confirmation', go: 'Sales' as const },
    { label: 'Confirmed bookings', value: confirmed.length, note: 'Allocated to customers', go: 'Sales' as const },
  ];
  const f = data.finance;
  const money = [
    { label: 'Sales this month', value: String(f.deliveredThisMonth), note: `${f.invoicesThisMonth} invoice${f.invoicesThisMonth === 1 ? '' : 's'} issued`, go: 'Finance' as const },
    { label: 'Revenue this month', value: inr(f.invoicedThisMonth), note: 'Invoiced, incl. GST', go: 'Finance' as const },
    { label: 'Collected this month', value: inr(f.collectedThisMonth), note: `${f.receiptsThisMonth} receipt${f.receiptsThisMonth === 1 ? '' : 's'} · advances ${inr(f.advancesHeld)}`, go: 'Finance' as const },
    { label: 'Pending dues', value: inr(f.outstandingDues), note: `${f.bookingsWithDues} invoiced booking${f.bookingsWithDues === 1 ? '' : 's'} unpaid`, go: 'Finance' as const, alert: f.outstandingDues > 0 },
  ];
  const recent = [...data.enquiries].sort((a, b) => b.id - a.id).slice(0, 5);

  return <View>
    <View style={s.headRow}>
      <Text style={s.title}>Performance snapshot</Text>
      <Pressable onPress={() => void load()}><Text style={s.link}>Refresh</Text></Pressable>
    </View>
    <Text style={s.scope}>{user.displayName} · {user.role.replace('_', ' ')} · {user.branch ?? 'All branches'}</Text>
    <Text style={s.group}>This month · money</Text>
    <View style={s.grid}>{money.map(m => <Pressable key={m.label} onPress={() => onOpen(m.go)} style={[s.metric, { width: wide ? '23.5%' : '48%' }]}>
      <Text style={s.metricLabel}>{m.label}</Text>
      <Text style={[s.metricValue, m.alert && { color: '#B42318' }]} numberOfLines={1} adjustsFontSizeToFit>{m.value}</Text>
      <Text style={s.metricNote}>{m.note}</Text>
    </Pressable>)}</View>
    <Text style={s.group}>Operations</Text>
    <View style={s.grid}>{metrics.map(m => <Pressable key={m.label} onPress={() => onOpen(m.go)} style={[s.metric, { width: wide ? '23.5%' : '48%' }]}>
      <Text style={s.metricLabel}>{m.label}</Text><Text style={s.metricValue}>{m.value}</Text><Text style={s.metricNote}>{m.note}</Text>
    </Pressable>)}</View>
    <Text style={s.title}>Recent enquiries</Text>
    {recent.length === 0 ? <View style={s.card}><Text style={s.muted}>No enquiries yet. Create one from Enquiries → + New.</Text></View>
      : recent.map(e => <Pressable key={e.id} onPress={() => onOpen('Enquiries')} style={s.row}>
        <View style={s.initials}><Text style={s.initialText}>{e.customerName.split(/\s+/).map(w => w[0]).join('').slice(0, 2).toUpperCase()}</Text></View>
        <View style={{ flex: 1 }}><Text style={s.rowTitle}>{e.customerName}</Text><Text style={s.muted}>{e.interestedModel} · {e.branch} · {e.salesperson}</Text></View>
        <Text style={[s.status, { color: stageColor[e.stage] ?? muted }]}>{e.stage}</Text>
      </Pressable>)}
  </View>;
}

const s = StyleSheet.create({
  headRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  title: { fontSize: 17, color: ink, fontWeight: '800', marginBottom: 8, marginTop: 6 },
  scope: { color: muted, fontSize: 12, marginBottom: 13 },
  group: { color: muted, fontSize: 11, fontWeight: '800', letterSpacing: 1, textTransform: 'uppercase', marginBottom: 8 },
  link: { color: teal, fontWeight: '700' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 12, marginBottom: 24 },
  metric: { backgroundColor: '#FFFFFF', borderRadius: 14, padding: 16, borderWidth: 1, borderColor: line, minHeight: 120 },
  metricLabel: { color: muted, fontSize: 11, fontWeight: '600' },
  metricValue: { color: ink, fontSize: 26, fontWeight: '900', marginTop: 5 },
  metricNote: { color: muted, fontSize: 10, marginTop: 4 },
  card: { backgroundColor: '#FFFFFF', borderRadius: 12, borderWidth: 1, borderColor: line, padding: 16, gap: 8 },
  error: { color: '#9A3B3B' },
  muted: { color: muted, fontSize: 11, marginTop: 3 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: '#FFFFFF', padding: 15, borderWidth: 1, borderColor: line, borderRadius: 12, marginBottom: 9 },
  initials: { height: 39, width: 39, borderRadius: 12, backgroundColor: '#DDF4ED', alignItems: 'center', justifyContent: 'center' },
  initialText: { color: teal, fontWeight: '800', fontSize: 12 },
  rowTitle: { color: ink, fontSize: 13, fontWeight: '800' },
  status: { fontSize: 10, fontWeight: '800', textAlign: 'right', maxWidth: 90 },
});
