import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import type { CurrentUser } from './api/enquiries';
import { inr, modeTitle } from './api/billing';
import { SalesReport as Report, reportApi } from './api/reports';
import { useBranches } from './api/branches';
import { toCsv } from './billingRules';
import { parseDmy, toDmy, todayIso } from './dates';

const ink = '#152F38', teal = '#087D72', muted = '#647882', line = '#E3EAED', red = '#B42318';

const iso = (d: Date) => d.toISOString().slice(0, 10);
function presetRange(id: string): [string, string] {
  const [y, m, d] = todayIso().split('-').map(Number);
  const utc = (yy: number, mm: number, dd: number) => new Date(Date.UTC(yy, mm, dd));
  switch (id) {
    case 'lastMonth': return [iso(utc(y, m - 2, 1)), iso(utc(y, m - 1, 0))];
    case 'fy': { const start = m >= 4 ? y : y - 1; return [`${start}-04-01`, todayIso()]; }
    case 'last30': return [iso(utc(y, m - 1, d - 29)), todayIso()];
    default: return [iso(utc(y, m - 1, 1)), todayIso()];
  }
}
const presets = [{ id: 'month', title: 'This month' }, { id: 'lastMonth', title: 'Last month' },
  { id: 'fy', title: 'This financial year' }, { id: 'last30', title: 'Last 30 days' }];

function Chips({ items, value, choose }: { items: { id: string; title: string }[]; value: string; choose: (v: string) => void }) {
  return <View style={s.chips}>{items.map(i => <Pressable key={i.id} onPress={() => choose(i.id)} style={[s.chip, value === i.id && s.chipOn]}>
    <Text style={[s.chipText, value === i.id && s.chipTextOn]}>{i.title}</Text></Pressable>)}</View>;
}

/** Sales register with filters and CSV export, for branch managers (own branch) and admins. */
export default function SalesReport({ user, wide }: { user: CurrentUser; wide: boolean }) {
  const allBranches = useBranches();
  const [from, setFrom] = useState(() => toDmy(presetRange('month')[0]));
  const [to, setTo] = useState(() => toDmy(presetRange('month')[1]));
  const [preset, setPreset] = useState('month');
  const [branch, setBranch] = useState('');
  const [model, setModel] = useState('');
  const [salesperson, setSalesperson] = useState('');
  const [report, setReport] = useState<Report | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const run = useCallback(async (f = from, t = to) => {
    const fIso = parseDmy(f), tIso = parseDmy(t);
    if (!fIso || !tIso) { setError('Enter both dates as DD/MM/YYYY.'); return; }
    if (tIso < fIso) { setError('The "To" date is before the "From" date.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      setReport(await reportApi.sales({ from: fIso, to: tIso, branch: branch || undefined, model: model || undefined,
        salesperson: salesperson || undefined }));
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not load the report'); }
    finally { setBusy(false); }
  }, [from, to, branch, model, salesperson]);
  useEffect(() => { void run(); }, [branch, model, salesperson]); // eslint-disable-line react-hooks/exhaustive-deps

  const choosePreset = (id: string) => {
    const [f, t] = presetRange(id).map(toDmy);
    setPreset(id); setFrom(f); setTo(t); void run(f, t);
  };

  const exportCsv = () => {
    if (!report) return;
    const csv = toCsv(
      ['Invoice No.', 'Source', 'Invoice date', 'Branch', 'Salesperson', 'Customer', 'Mobile', 'Model', 'Variant', 'Colour',
        'Chassis / frame', 'Battery serials', 'Taxable value', 'GST', 'Subsidy', 'Grand total', 'Received', 'Balance due',
        'Payment modes', 'Finance', 'Status', 'Delivered on'],
      report.rows.map(r => [r.invoiceNumber, r.source === 'PAPER' ? 'Paper invoice' : 'App', toDmy(r.invoiceDate), r.branch,
        r.salesperson, r.customerName, r.customerPhone, r.model, r.variant, r.colour, r.chassis, r.batterySerials,
        r.taxableValue.toFixed(2), r.gst.toFixed(2), r.subsidy.toFixed(2), r.grandTotal.toFixed(2), r.received.toFixed(2),
        r.balance.toFixed(2), r.paymentModes.split(', ').filter(Boolean).map(modeTitle).join(' + '), r.financier, r.bookingStatus,
        toDmy(r.deliveredOn)]));
    if (Platform.OS !== 'web' || typeof document === 'undefined') { setError('Export is available in the web app.'); return; }
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = `sales_${toDmy(report.from).replace(/\//g, '-')}_to_${toDmy(report.to).replace(/\//g, '-')}${report.branch ? '_' + report.branch : ''}.csv`;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setNotice(`Downloaded ${report.rows.length} sale${report.rows.length === 1 ? '' : 's'}.`);
  };

  const branchOptions = user.role === 'ADMIN' ? allBranches : [user.branch ?? ''];
  const t = report?.totals;
  return <View>
    <View style={s.card}>
      <Text style={s.title}>Sales report</Text>
      <Text style={s.muted}>Invoiced sales (app and paper) by invoice date{user.role === 'ADMIN' ? '' : ' · ' + user.branch}.</Text>
      <Chips items={presets} value={preset} choose={choosePreset} />
      <View style={s.row}>
        <View style={s.half}><Text style={s.label}>From (DD/MM/YYYY)</Text>
          <TextInput style={s.input} value={from} onChangeText={v => { setFrom(v); setPreset(''); }} /></View>
        <View style={s.half}><Text style={s.label}>To (DD/MM/YYYY)</Text>
          <TextInput style={s.input} value={to} onChangeText={v => { setTo(v); setPreset(''); }} /></View>
      </View>
      {user.role === 'ADMIN' && <><Text style={s.label}>Branch</Text>
        <Chips items={[{ id: '', title: 'All branches' }, ...branchOptions.map(b => ({ id: b, title: b }))]} value={branch} choose={setBranch} /></>}
      <Text style={s.label}>Model</Text>
      <Chips items={[{ id: '', title: 'All models' }, ...(report?.options.models ?? []).map(m => ({ id: m, title: m }))]} value={model} choose={setModel} />
      <Text style={s.label}>Salesperson</Text>
      <Chips items={[{ id: '', title: 'Everyone' }, ...(report?.options.salespeople ?? []).map(p => ({ id: p, title: p }))]} value={salesperson} choose={setSalesperson} />
      <View style={s.row}>
        <Pressable disabled={busy} onPress={() => void run()} style={s.primary}><Text style={s.primaryText}>Show report</Text></Pressable>
        <Pressable disabled={busy || !report?.rows.length} onPress={exportCsv} style={[s.secondary, !report?.rows.length && { opacity: 0.5 }]}>
          <Text style={s.secondaryText}>Export to Excel (CSV)</Text></Pressable>
      </View>
      {busy && <ActivityIndicator color={teal} />}
      {!!error && <Text style={s.error}>{error}</Text>}
      {!!notice && <Text style={s.success}>{notice}</Text>}
    </View>

    {report && t && <>
      <Text style={s.sub}>{toDmy(report.from)} to {toDmy(report.to)}{report.branch ? ' · ' + report.branch : ''}{report.model ? ' · ' + report.model : ''}{report.salesperson ? ' · ' + report.salesperson : ''}</Text>
      <View style={s.grid}>{[
        { label: 'Vehicles sold', value: String(t.count) },
        { label: 'Sales value (incl. GST)', value: inr(t.grandTotal) },
        { label: 'Received', value: inr(t.received) },
        { label: 'Balance due', value: inr(t.balance), alert: t.balance > 0 },
      ].map(c => <View key={c.label} style={[s.metric, { width: wide ? '23.5%' : '48%' }]}>
        <Text style={s.metricLabel}>{c.label}</Text>
        <Text style={[s.metricValue, c.alert && { color: red }]} numberOfLines={1} adjustsFontSizeToFit>{c.value}</Text>
      </View>)}</View>
      <Text style={s.muted}>Taxable {inr(t.taxableValue)} · GST {inr(t.gst)}{t.subsidy ? ' · subsidy ' + inr(t.subsidy) : ''}</Text>

      {([['By model', report.byModel], ...(user.role === 'ADMIN' ? [['By branch', report.byBranch]] : []), ['By salesperson', report.bySalesperson]] as [string, Report['byModel']][])
        .filter(([, g]) => g.length).map(([title, groups]) => <View key={title} style={s.card}>
          <Text style={s.sectionTitle}>{title}</Text>
          {groups.map(g => <View key={g.name} style={s.line}>
            <Text style={s.lineName}>{g.name} · {g.count}</Text>
            <Text style={s.lineValue}>{inr(g.grandTotal)}{g.balance > 0 ? ` · due ${inr(g.balance)}` : ''}</Text>
          </View>)}
        </View>)}

      <Text style={s.sectionTitle}>Sales ({report.rows.length})</Text>
      {!report.rows.length && <View style={s.card}><Text style={s.muted}>No invoiced sales match these filters.</Text></View>}
      {report.rows.map(r => <View key={r.bookingId} style={s.saleRow}>
        <View style={{ flex: 1 }}>
          <Text style={s.lineName}>{r.customerName} · {r.model} {r.variant}</Text>
          <Text style={s.muted}>{r.invoiceNumber}{r.source === 'PAPER' ? ' (paper)' : ''} · {toDmy(r.invoiceDate)} · {r.branch} · {r.salesperson}</Text>
          <Text style={s.muted}>{r.chassis}{r.paymentModes ? ' · ' + r.paymentModes.split(', ').map(modeTitle).join(' + ') : ''}{r.financier ? ' · ' + r.financier : ''}</Text>
        </View>
        <View style={{ alignItems: 'flex-end' }}>
          <Text style={s.lineValue}>{inr(r.grandTotal)}</Text>
          {r.balance > 0 && <Text style={[s.muted, { color: red }]}>due {inr(r.balance)}</Text>}
        </View>
      </View>)}
    </>}
  </View>;
}

const s = StyleSheet.create({
  card: { backgroundColor: '#FFF', borderRadius: 12, borderWidth: 1, borderColor: line, padding: 14, marginBottom: 12 },
  title: { fontSize: 17, color: ink, fontWeight: '800' },
  sectionTitle: { fontSize: 15, color: ink, fontWeight: '800', marginBottom: 8, marginTop: 6 },
  sub: { color: ink, fontSize: 13, fontWeight: '700', marginBottom: 10 },
  muted: { color: muted, fontSize: 11, marginTop: 3 },
  label: { fontSize: 12, color: ink, fontWeight: '700', marginTop: 10, marginBottom: 6 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, alignItems: 'center' },
  half: { width: '48%', flexGrow: 1 },
  input: { borderWidth: 1, borderColor: line, backgroundColor: '#FBFDFD', padding: 11, borderRadius: 9, color: ink, fontSize: 13 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginTop: 10 },
  chip: { padding: 9, borderColor: line, borderWidth: 1, borderRadius: 9, backgroundColor: '#FFF' },
  chipOn: { backgroundColor: teal, borderColor: teal }, chipText: { color: ink, fontSize: 11 }, chipTextOn: { color: '#FFF' },
  primary: { backgroundColor: teal, borderRadius: 9, padding: 13, marginTop: 14 },
  primaryText: { fontSize: 12, fontWeight: '800', color: '#FFF' },
  secondary: { backgroundColor: '#E4F3EE', borderRadius: 9, padding: 12, marginTop: 14 },
  secondaryText: { fontSize: 12, fontWeight: '800', color: teal },
  error: { color: red, padding: 10, backgroundColor: '#FDE8E8', borderRadius: 9, marginTop: 10 },
  success: { color: teal, fontSize: 12, fontWeight: '700', marginTop: 10 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 12, marginBottom: 6 },
  metric: { backgroundColor: '#FFF', borderRadius: 14, padding: 15, borderWidth: 1, borderColor: line, minHeight: 86 },
  metricLabel: { color: muted, fontSize: 11, fontWeight: '600' },
  metricValue: { color: ink, fontSize: 20, fontWeight: '900', marginTop: 5 },
  line: { flexDirection: 'row', justifyContent: 'space-between', gap: 10, paddingVertical: 5, borderBottomWidth: 1, borderBottomColor: '#F1F5F6' },
  lineName: { color: ink, fontSize: 13, fontWeight: '800', flexShrink: 1 },
  lineValue: { color: ink, fontSize: 13, fontWeight: '900' },
  saleRow: { flexDirection: 'row', gap: 10, backgroundColor: '#FFF', padding: 13, borderWidth: 1, borderColor: line, borderRadius: 12, marginBottom: 8 },
});
