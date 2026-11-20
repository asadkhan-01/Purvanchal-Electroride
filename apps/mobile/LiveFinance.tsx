import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import type { CurrentUser } from './api/enquiries';
import { FinanceSummary, billingApi, inr, modeTitle } from './api/billing';

const ink = '#152F38', teal = '#087D72', muted = '#647882', line = '#E3EAED', red = '#B42318';

const shift = (ym: string, by: number) => {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(Date.UTC(y, m - 1 + by, 1));
  return d.toISOString().slice(0, 7);
};
const monthName = (ym: string) => new Date(ym + '-01T00:00:00Z').toLocaleDateString('en-IN', { month: 'long', year: 'numeric', timeZone: 'UTC' });

/** Collections, invoicing and dues for the bookings the signed-in user can see. */
export default function LiveFinance({ user, wide, onOpenSales }: { user: CurrentUser; wide: boolean; onOpenSales: () => void }) {
  const [month, setMonth] = useState<string | null>(null);
  const [data, setData] = useState<FinanceSummary | null>(null);
  const [error, setError] = useState('');
  const load = useCallback(async (m: string | null) => {
    setError('');
    try { const d = await billingApi.summary(m ?? undefined); setData(d); if (!m) setMonth(d.month); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to load finance summary'); }
  }, []);
  useEffect(() => { void load(month); }, [user.username, month, load]);

  if (error) return <View style={s.card}><Text style={{ color: red }}>{error}</Text></View>;
  if (!data) return <ActivityIndicator color={teal} />;
  const cards = [
    { label: 'Collected', value: inr(data.collectedThisMonth), note: `${data.receiptsThisMonth} receipt${data.receiptsThisMonth === 1 ? '' : 's'} in ${monthName(data.month)}` },
    { label: 'Invoiced', value: inr(data.invoicedThisMonth), note: `${data.invoicesThisMonth} invoice${data.invoicesThisMonth === 1 ? '' : 's'} · ${data.deliveredThisMonth} delivered` },
    { label: 'Outstanding dues', value: inr(data.outstandingDues), note: `${data.bookingsWithDues} invoiced booking${data.bookingsWithDues === 1 ? '' : 's'} unpaid`, alert: data.outstandingDues > 0 },
    { label: 'Advances held', value: inr(data.advancesHeld), note: 'Received on bookings not yet invoiced' },
  ];
  const modes = Object.entries(data.collectedByMode);

  return <View>
    <Text style={s.scope}>{user.displayName} · {user.role.replace('_', ' ')} · {user.branch ?? 'All branches'}</Text>
    <View style={s.monthRow}>
      <Pressable onPress={() => setMonth(shift(data.month, -1))} style={s.monthBtn}><Text style={s.link}>‹ Prev</Text></Pressable>
      <Text style={s.month}>{monthName(data.month)}</Text>
      <Pressable onPress={() => setMonth(shift(data.month, 1))} style={s.monthBtn}><Text style={s.link}>Next ›</Text></Pressable>
    </View>
    <View style={s.grid}>{cards.map(c => <View key={c.label} style={[s.metric, { width: wide ? '23.5%' : '48%' }]}>
      <Text style={s.metricLabel}>{c.label}</Text>
      <Text style={[s.metricValue, c.alert && { color: red }]} numberOfLines={1} adjustsFontSizeToFit>{c.value}</Text>
      <Text style={s.metricNote}>{c.note}</Text></View>)}</View>

    <Text style={s.title}>Collections by mode</Text>
    <View style={s.card}>{modes.length ? modes.map(([m, v]) => <View key={m} style={s.line}><Text style={s.rowText}>{modeTitle(m)}</Text><Text style={s.rowValue}>{inr(v)}</Text></View>)
      : <Text style={s.muted}>No collections in {monthName(data.month)}.</Text>}</View>

    <Text style={s.title}>Dues</Text>
    {data.dues.length === 0 ? <View style={s.card}><Text style={s.muted}>No outstanding balances.</Text></View>
      : data.dues.map(d => <Pressable key={d.bookingId} onPress={onOpenSales} style={s.row}>
        <View style={{ flex: 1 }}>
          <Text style={s.rowText}>{d.customerName} · Booking #{d.bookingId}</Text>
          <Text style={s.muted}>{d.invoiceNumber ?? 'Not invoiced (estimate)'} · {d.branch} · {d.salesperson}</Text>
          <Text style={s.muted}>Payable {inr(d.payable)} · Received {inr(d.received)}</Text>
        </View>
        <Text style={[s.rowValue, { color: d.estimated ? muted : red }]}>{inr(d.balance)}</Text>
      </Pressable>)}

    <Text style={s.title}>Recent receipts</Text>
    {data.recentReceipts.length === 0 ? <View style={s.card}><Text style={s.muted}>No receipts yet.</Text></View>
      : data.recentReceipts.map(r => <View key={r.id} style={[s.row, r.status === 'VOIDED' && { opacity: 0.55 }]}>
        <View style={{ flex: 1 }}>
          <Text style={s.rowText}>{r.customerName}{r.status === 'VOIDED' ? ' · VOIDED' : ''}</Text>
          <Text style={s.muted}>{r.receiptNumber} · {r.receivedOn} · {modeTitle(r.mode)}{r.reference ? ' · ' + r.reference : ''}</Text>
        </View>
        <Text style={s.rowValue}>{inr(r.amount)}</Text>
      </View>)}
  </View>;
}

const s = StyleSheet.create({
  banner: { padding: 11, borderRadius: 9, backgroundColor: '#FFF1D8', marginBottom: 12 },
  bannerText: { fontSize: 11, color: '#805915', fontWeight: '700' },
  scope: { color: muted, fontSize: 12, marginBottom: 8 },
  monthRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  monthBtn: { padding: 8 }, month: { color: ink, fontSize: 15, fontWeight: '800' },
  title: { fontSize: 16, color: ink, fontWeight: '800', marginBottom: 8, marginTop: 10 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 12, marginBottom: 12 },
  metric: { backgroundColor: '#FFF', borderRadius: 14, padding: 15, borderWidth: 1, borderColor: line, minHeight: 112 },
  metricLabel: { color: muted, fontSize: 11, fontWeight: '600' },
  metricValue: { color: ink, fontSize: 20, fontWeight: '900', marginTop: 5 },
  metricNote: { color: muted, fontSize: 10, marginTop: 4 },
  card: { backgroundColor: '#FFF', borderRadius: 12, borderWidth: 1, borderColor: line, padding: 14, gap: 6 },
  line: { flexDirection: 'row', justifyContent: 'space-between' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: '#FFF', padding: 13, borderWidth: 1, borderColor: line, borderRadius: 12, marginBottom: 8 },
  rowText: { color: ink, fontSize: 13, fontWeight: '800' },
  rowValue: { color: ink, fontSize: 13, fontWeight: '900' },
  muted: { color: muted, fontSize: 11, marginTop: 2 },
  link: { color: teal, fontWeight: '700' },
});
