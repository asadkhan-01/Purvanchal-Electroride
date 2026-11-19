import React, { useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';

type Role = 'Salesperson' | 'Branch Manager' | 'Administrator';
type Stage = 'New' | 'Contacted' | 'Interested' | 'Booked' | 'Lost';
type Enquiry = {
  id: number; name: string; phone: string; branch: string; salesperson: string;
  source: string; model: string; stage: Stage; followUp: string; notes: string;
  history: string[];
};
const initial: Enquiry[] = [
  { id: 1, name: 'Rahul Kumar', phone: '9876500001', branch: 'Varanasi', salesperson: 'Aman', source: 'Walk-in', model: 'Electric Scooter — Standard', stage: 'Interested', followUp: '2026-11-21', notes: 'Asked about range and warranty', history: ['Enquiry created from walk-in', 'Discussed range and warranty'] },
  { id: 2, name: 'Farhan Ansari', phone: '9876500002', branch: 'Ghazipur', salesperson: 'Sana', source: 'Phone', model: 'Electric Scooter — Long Range', stage: 'Contacted', followUp: '2026-11-22', notes: 'Call after 5 PM', history: ['Phone enquiry created'] },
  { id: 3, name: 'Sneha Patel', phone: '9876500003', branch: 'Varanasi', salesperson: 'Aman', source: 'Referral', model: 'Electric Scooter — Premium', stage: 'New', followUp: '2026-11-20', notes: '', history: ['Referral enquiry created'] },
  { id: 4, name: 'Imran Ali', phone: '9876500004', branch: 'Varanasi', salesperson: 'Neha', source: 'Website', model: 'Electric Scooter — Standard', stage: 'Booked', followUp: '', notes: 'Booking confirmed', history: ['Website enquiry created', 'Booking confirmed'] },
];
const branches = ['Varanasi', 'Ghazipur', 'Mau'];
const stages: Stage[] = ['New', 'Contacted', 'Interested', 'Booked', 'Lost'];
const c = { ink: '#18343D', teal: '#087D72', muted: '#637780', border: '#DCE6E9', white: '#FFFFFF', pale: '#E5F4EF', bg: '#F4F7F8' };
const blank = { name: '', phone: '', branch: 'Varanasi', salesperson: 'Aman', source: 'Walk-in', model: '', notes: '', followUp: '' };
function Field({ label, value, onChangeText, placeholder, keyboardType }: { label: string; value: string; onChangeText: (v: string) => void; placeholder?: string; keyboardType?: 'default' | 'phone-pad' }) {
  return <View style={s.field}><Text style={s.fieldLabel}>{label}</Text><TextInput value={value} onChangeText={onChangeText} placeholder={placeholder} placeholderTextColor="#91A2A9" keyboardType={keyboardType || 'default'} style={s.input}/></View>;
}
function Choices<T extends string>({ values, value, onSelect }: { values: readonly T[]; value: T; onSelect: (v: T) => void }) {
  return <View style={s.chips}>{values.map(v => <Pressable key={v} onPress={() => onSelect(v)} style={[s.chip, value === v && s.chipActive]}><Text style={[s.chipText, value === v && s.chipTextActive]}>{v}</Text></Pressable>)}</View>;
}
export default function CRM() {
  const [records, setRecords] = useState<Enquiry[]>(initial);
  const [role, setRole] = useState<Role>('Administrator');
  const [currentBranch, setCurrentBranch] = useState('Varanasi');
  const [currentSalesperson, setCurrentSalesperson] = useState('Aman');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<'All' | Stage>('All');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState(blank);
  const [activity, setActivity] = useState('');
  const [nextDate, setNextDate] = useState('');
  const visible = useMemo(() => records.filter(e => {
    if (role === 'Salesperson' && (e.branch !== currentBranch || e.salesperson !== currentSalesperson)) return false;
    if (role === 'Branch Manager' && e.branch !== currentBranch) return false;
    return true;
  }), [records, role, currentBranch, currentSalesperson]);
  const filtered = visible.filter(e => {
    const match = [e.name, e.phone, e.branch, e.salesperson, e.model, e.source].join(' ').toLowerCase().includes(query.toLowerCase());
    return match && (filter === 'All' || e.stage === filter);
  });
  const selected = visible.find(e => e.id === selectedId);
  const update = (id: number, patch: Partial<Enquiry>, event?: string) => setRecords(prev => prev.map(e => e.id === id ? { ...e, ...patch, history: event ? [...e.history, event] : e.history } : e));
  const save = () => {
    if (!draft.name.trim() || !/^\d{10}$/.test(draft.phone.trim()) || !draft.model.trim()) {
      Alert.alert('Check enquiry', 'Enter customer name, a 10-digit phone number and interested model.');
      return;
    }
    if (role !== 'Administrator' && draft.branch !== currentBranch) {
      Alert.alert('Branch restriction', 'Select your assigned branch.');
      return;
    }
    if (role === 'Salesperson' && draft.salesperson !== currentSalesperson) {
      Alert.alert('Assignment restriction', 'Salespeople can assign demo enquiries only to themselves.');
      return;
    }
    const id = Math.max(0, ...records.map(r => r.id)) + 1;
    setRecords(prev => [{ id, ...draft, stage: 'New', history: ['Enquiry created'] }, ...prev]);
    setCreating(false); setDraft(blank); setSelectedId(id);
  };
  return <View style={s.root}>
    <View style={s.notice}><Text style={s.noticeText}>DEMO CRM · Changes are temporary and reset when the app reloads. Role switching is not security.</Text></View>
    <Text style={s.heading}>Enquiry management</Text>
    <Text style={s.sub}>Capture leads, track follow-ups and manage your sales pipeline.</Text>
    <Text style={s.fieldLabel}>Preview access as</Text>
    <Choices values={['Salesperson', 'Branch Manager', 'Administrator'] as const} value={role} onSelect={r => { setRole(r); setSelectedId(null); setCreating(false); }}/>
    {role !== 'Administrator' && <><Text style={s.fieldLabel}>Current branch</Text><Choices values={branches} value={currentBranch} onSelect={b => { setCurrentBranch(b); setSelectedId(null); }}/></>}
    {role === 'Salesperson' && <><Text style={s.fieldLabel}>Current salesperson</Text><Choices values={['Aman', 'Sana', 'Neha']} value={currentSalesperson} onSelect={p => { setCurrentSalesperson(p); setSelectedId(null); }}/></>}
    <View style={s.toolbar}><Text style={s.count}>{visible.length} accessible enquiries</Text><Pressable onPress={() => { setCreating(true); setSelectedId(null); setDraft({ ...blank, branch: role === 'Administrator' ? 'Varanasi' : currentBranch, salesperson: role === 'Salesperson' ? currentSalesperson : 'Aman' }); }} style={s.primary}><Text style={s.primaryText}>+ New enquiry</Text></Pressable></View>
    {creating ? <View style={s.panel}>
      <Text style={s.panelTitle}>Create enquiry</Text>
      <Field label="Customer name *" value={draft.name} onChangeText={name => setDraft({ ...draft, name })}/>
      <Field label="Mobile number *" value={draft.phone} onChangeText={phone => setDraft({ ...draft, phone })} keyboardType="phone-pad"/>
      <Field label="Interested EV model *" value={draft.model} onChangeText={model => setDraft({ ...draft, model })} placeholder="Model / variant"/>
      <Text style={s.fieldLabel}>Branch</Text><Choices values={role === 'Administrator' ? branches : [currentBranch]} value={draft.branch} onSelect={branch => setDraft({ ...draft, branch })}/>
      <Text style={s.fieldLabel}>Assigned salesperson</Text><Choices values={role === 'Salesperson' ? [currentSalesperson] : ['Aman', 'Sana', 'Neha']} value={draft.salesperson} onSelect={salesperson => setDraft({ ...draft, salesperson })}/>
      <Text style={s.fieldLabel}>Enquiry source</Text><Choices values={['Walk-in', 'Phone', 'Website', 'Referral']} value={draft.source} onSelect={source => setDraft({ ...draft, source })}/>
      <Field label="Follow-up date (YYYY-MM-DD)" value={draft.followUp} onChangeText={followUp => setDraft({ ...draft, followUp })}/>
      <Field label="Notes" value={draft.notes} onChangeText={notes => setDraft({ ...draft, notes })}/>
      <View style={s.actions}><Pressable onPress={save} style={s.primary}><Text style={s.primaryText}>Save enquiry</Text></Pressable><Pressable onPress={() => setCreating(false)} style={s.secondary}><Text style={s.secondaryText}>Cancel</Text></Pressable></View>
    </View> : selected ? <View style={s.panel}>
      <Pressable onPress={() => { setSelectedId(null); setActivity(''); setNextDate(''); }}><Text style={s.back}>← All enquiries</Text></Pressable>
      <Text style={s.panelTitle}>{selected.name}</Text><Text style={s.sub}>{selected.phone} · {selected.branch} · Assigned to {selected.salesperson}</Text>
      <Text style={s.detail}>Model: {selected.model}</Text><Text style={s.detail}>Source: {selected.source}</Text><Text style={s.detail}>Next follow-up: {selected.followUp || 'Not scheduled'}</Text>
      <Text style={s.fieldLabel}>Update pipeline stage</Text><Choices values={stages} value={selected.stage} onSelect={stage => update(selected.id, { stage }, 'Stage changed to ' + stage)}/>
      <Field label="New activity / call outcome" value={activity} onChangeText={setActivity} placeholder="Describe the customer interaction"/>
      <Pressable onPress={() => { if (activity.trim()) { update(selected.id, {}, activity.trim()); setActivity(''); } }} style={s.primary}><Text style={s.primaryText}>Add activity</Text></Pressable>
      <View style={{ height: 15 }}/>
      <Field label="Next follow-up (YYYY-MM-DD)" value={nextDate} onChangeText={setNextDate} placeholder="2026-11-25"/>
      <Pressable onPress={() => { if (/^\d{4}-\d{2}-\d{2}$/.test(nextDate)) { update(selected.id, { followUp: nextDate }, 'Follow-up scheduled for ' + nextDate); setNextDate(''); } else Alert.alert('Invalid date', 'Enter a date as YYYY-MM-DD.'); }} style={s.secondary}><Text style={s.secondaryText}>Schedule follow-up</Text></Pressable>
      <Text style={[s.panelTitle, { marginTop: 22 }]}>Activity history</Text>
      {selected.history.slice().reverse().map((h, i) => <View key={i} style={s.history}><Text style={s.historyBullet}>●</Text><Text style={s.historyText}>{h}</Text></View>)}
    </View> : <>
      <Field label="Search enquiries" value={query} onChangeText={setQuery} placeholder="Name, phone, model, branch, salesperson"/>
      <ScrollView horizontal showsHorizontalScrollIndicator={false}><Choices values={['All', ...stages] as ('All' | Stage)[]} value={filter} onSelect={setFilter}/></ScrollView>
      {filtered.map(e => <Pressable key={e.id} onPress={() => setSelectedId(e.id)} style={s.record}>
        <View style={s.recordTop}><Text style={s.recordName}>{e.name}</Text><Text style={s.stage}>{e.stage}</Text></View>
        <Text style={s.recordInfo}>{e.phone} · {e.branch} · {e.salesperson}</Text>
        <Text style={s.recordInfo}>{e.model}</Text>
        <Text style={s.follow}>Follow-up: {e.followUp || 'Not scheduled'}  →</Text>
      </Pressable>)}
      {filtered.length === 0 && <Text style={s.empty}>No enquiries match your filters or access scope.</Text>}
    </>}
  </View>;
}
const s = StyleSheet.create({
  root: { paddingBottom: 22 }, notice: { backgroundColor: '#FFF1D8', padding: 12, borderRadius: 10, marginBottom: 18 },
  noticeText: { color: '#805915', fontSize: 11, fontWeight: '700' },
  heading: { fontSize: 23, fontWeight: '800', color: c.ink }, sub: { color: c.muted, fontSize: 12, marginTop: 6, marginBottom: 17, lineHeight: 19 },
  fieldLabel: { fontSize: 12, fontWeight: '700', color: c.ink, marginBottom: 8, marginTop: 11 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 8 },
  chip: { borderRadius: 9, backgroundColor: c.white, paddingVertical: 9, paddingHorizontal: 12, borderWidth: 1, borderColor: c.border },
  chipActive: { backgroundColor: c.teal, borderColor: c.teal }, chipText: { fontSize: 11, color: c.ink, fontWeight: '600' }, chipTextActive: { color: c.white },
  toolbar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginVertical: 19, gap: 10 },
  count: { fontSize: 12, fontWeight: '700', color: c.ink, flexShrink: 1 },
  primary: { backgroundColor: c.teal, borderRadius: 9, paddingVertical: 12, paddingHorizontal: 16, alignSelf: 'flex-start' },
  primaryText: { color: c.white, fontSize: 12, fontWeight: '800' },
  secondary: { backgroundColor: c.pale, borderRadius: 9, paddingVertical: 12, paddingHorizontal: 16, alignSelf: 'flex-start' },
  secondaryText: { color: c.teal, fontSize: 12, fontWeight: '800' },
  panel: { backgroundColor: c.white, borderWidth: 1, borderColor: c.border, padding: 18, borderRadius: 14 },
  panelTitle: { color: c.ink, fontSize: 18, fontWeight: '800', marginBottom: 12 },
  field: { marginBottom: 7 }, input: { borderWidth: 1, borderColor: c.border, backgroundColor: '#FBFDFD', borderRadius: 9, padding: 12, fontSize: 13, color: c.ink },
  actions: { flexDirection: 'row', gap: 12, marginTop: 14 }, back: { color: c.teal, fontWeight: '700', marginBottom: 18 },
  detail: { color: c.ink, fontSize: 13, marginVertical: 5 },
  record: { backgroundColor: c.white, borderRadius: 12, borderWidth: 1, borderColor: c.border, padding: 17, marginBottom: 10 },
  recordTop: { flexDirection: 'row', justifyContent: 'space-between', gap: 8, marginBottom: 7 },
  recordName: { color: c.ink, fontWeight: '800', fontSize: 15 }, stage: { color: c.teal, fontSize: 11, fontWeight: '800' },
  recordInfo: { color: c.muted, fontSize: 12, marginTop: 4 }, follow: { color: c.teal, fontWeight: '700', fontSize: 11, marginTop: 12 },
  history: { flexDirection: 'row', gap: 10, borderLeftWidth: 1, borderLeftColor: c.border, paddingVertical: 9, paddingLeft: 12 },
  historyBullet: { color: c.teal, fontSize: 10 }, historyText: { color: c.ink, fontSize: 12, flex: 1 },
  empty: { color: c.muted, padding: 20 },
});
