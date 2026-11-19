import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Activity, CurrentUser, Enquiry, NewEnquiry, Stage, enquiryApi } from './api/enquiries';

const stages: Stage[] = ['New', 'Contacted', 'Interested', 'Booked', 'Lost'];
const branches = ['Varanasi', 'Ghazipur', 'Mau'];
const salespeople = ['Aman', 'Sana', 'Neha'];
const sources = ['Walk-in', 'Phone', 'Website', 'Referral'];
const empty: NewEnquiry = { customerName: '', phone: '', branch: 'Varanasi', salesperson: 'Aman',
  source: 'Walk-in', interestedModel: '', notes: '', followUpDate: null };
const ink = '#18343D', teal = '#087D72', muted = '#637780', border = '#DCE6E9';
function Field({ label, value, onChange, placeholder }: {
  label: string; value: string; onChange: (value: string) => void; placeholder?: string;
}) {
  return <View style={styles.field}><Text style={styles.label}>{label}</Text>
    <TextInput style={styles.input} value={value} onChangeText={onChange} placeholder={placeholder} placeholderTextColor="#91A2A9" /></View>;
}
function Choices<T extends string>({ items, value, choose }: { items: readonly T[]; value: T; choose: (item: T) => void }) {
  return <View style={styles.options}>{items.map(item => <Pressable key={item}
    style={[styles.chip, value === item && styles.chipOn]} onPress={() => choose(item)}>
    <Text style={[styles.chipText, value === item && styles.chipTextOn]}>{item}</Text>
  </Pressable>)}</View>;
}
export default function LiveCRM({ user }: { user: CurrentUser }) {
  const [items, setItems] = useState<Enquiry[]>([]);
  const [events, setEvents] = useState<Activity[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState<NewEnquiry>(() => ({ ...empty,
    branch: user.branch || 'Varanasi',
    salesperson: user.role === 'SALESPERSON' ? user.displayName : 'Aman',
  }));
  const [query, setQuery] = useState('');
  const [newActivity, setNewActivity] = useState('');
  const [newFollowUp, setNewFollowUp] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const reload = useCallback(async () => {
    setBusy(true); setError('');
    try { setItems(await enquiryApi.list()); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to load enquiries'); }
    finally { setBusy(false); }
  }, []);

  useEffect(() => { void reload(); }, [user.username, reload]);

  const loadEvents = useCallback(async (id: number) => {
    try { setEvents(await enquiryApi.activities(id)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not load activities'); }
  }, []);

  useEffect(() => {
    if (selectedId !== null) void loadEvents(selectedId);
    else setEvents([]);
  }, [selectedId, loadEvents]);

  const selected = items.find(e => e.id === selectedId);
  const visible = useMemo(() => items.filter(e =>
    [e.customerName, e.phone, e.branch, e.salesperson, e.interestedModel]
      .join(' ').toLowerCase().includes(query.toLowerCase())), [items, query]);

  const execute = async (job: () => Promise<unknown>, refreshEvents = false) => {
    setBusy(true); setError('');
    try {
      await job();
      await reload();
      if (refreshEvents && selectedId !== null) await loadEvents(selectedId);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Action failed');
      return false;
    } finally { setBusy(false); }
  };

  const create = async () => {
    if (!draft.customerName.trim() || !/^\d{10}$/.test(draft.phone) || !draft.interestedModel.trim()) {
      setError('Enter a name, 10-digit phone number and interested EV model.'); return;
    }
    if (draft.followUpDate && !/^\d{4}-\d{2}-\d{2}$/.test(draft.followUpDate)) {
      setError('Follow-up date must use YYYY-MM-DD.'); return;
    }
    let created: Enquiry | null = null;
    const ok = await execute(async () => { created = await enquiryApi.create(draft); });
    if (ok) { setAdding(false); setDraft({ ...empty, branch: user.branch || 'Varanasi', salesperson: user.role === 'SALESPERSON' ? user.displayName : 'Aman' }); if (created) setSelectedId((created as Enquiry).id); }
  };

  return <View style={styles.root}>
    <View style={styles.warning}><Text style={styles.warningText}>DEVELOPMENT AUTH · Basic authentication and backend-enforced branch visibility. Not production-ready. Synthetic records only.</Text></View>
    <Text style={styles.title}>Enquiry management</Text>
    <Text style={styles.description}>{user.displayName} · {user.role} {user.branch ? '· ' + user.branch : '· All branches'}</Text>
    <Text style={styles.description}>Authenticated Spring Boot API · PostgreSQL persistence</Text>
    {error ? <View style={styles.error}><Text style={styles.errorText}>{error}</Text>
      <Text style={styles.small}>Check API port visibility, URL, CORS and backend health.</Text></View> : null}
    <View style={styles.toolbar}><Text style={styles.label}>{items.length} enquiries</Text>
      <Pressable disabled={busy} onPress={() => void reload()} style={styles.light}><Text style={styles.lightText}>Refresh</Text></Pressable>
      <Pressable disabled={busy} onPress={() => { setSelectedId(null); setAdding(true); }} style={styles.primary}><Text style={styles.primaryText}>+ New</Text></Pressable>
    </View>
    {busy && <ActivityIndicator color={teal} />}
    {adding ? <View style={styles.card}>
      <Text style={styles.cardTitle}>New enquiry</Text>
      <Field label="Customer name *" value={draft.customerName} onChange={customerName => setDraft({ ...draft, customerName })}/>
      <Field label="Mobile number *" value={draft.phone} onChange={phone => setDraft({ ...draft, phone })}/>
      <Field label="Interested model / variant *" value={draft.interestedModel} onChange={interestedModel => setDraft({ ...draft, interestedModel })}/>
      <Text style={styles.label}>Branch</Text><Choices items={user.role === 'ADMIN' ? branches : [user.branch || 'Varanasi']} value={draft.branch} choose={branch => setDraft({ ...draft, branch })}/>
      <Text style={styles.label}>Salesperson</Text><Choices items={user.role === 'SALESPERSON' ? [user.displayName] : salespeople} value={draft.salesperson} choose={salesperson => setDraft({ ...draft, salesperson })}/>
      <Text style={styles.label}>Source</Text><Choices items={sources} value={draft.source} choose={source => setDraft({ ...draft, source })}/>
      <Field label="Follow-up date (YYYY-MM-DD)" value={draft.followUpDate || ''} onChange={followUpDate => setDraft({ ...draft, followUpDate: followUpDate || null })}/>
      <Field label="Notes" value={draft.notes || ''} onChange={notes => setDraft({ ...draft, notes })}/>
      <View style={styles.actions}><Pressable disabled={busy} onPress={() => void create()} style={styles.primary}><Text style={styles.primaryText}>Save enquiry</Text></Pressable>
        <Pressable onPress={() => setAdding(false)} style={styles.light}><Text style={styles.lightText}>Cancel</Text></Pressable></View>
    </View> : selected ? <View style={styles.card}>
      <Pressable onPress={() => setSelectedId(null)}><Text style={styles.link}>← Back to enquiries</Text></Pressable>
      <Text style={styles.cardTitle}>{selected.customerName}</Text>
      <Text style={styles.description}>{selected.phone} · {selected.branch} · {selected.salesperson}</Text>
      <Text style={styles.info}>Model: {selected.interestedModel}</Text>
      <Text style={styles.info}>Source: {selected.source}</Text>
      <Text style={styles.info}>Follow-up: {selected.followUpDate || 'Not scheduled'}</Text>
      <Text style={styles.label}>Pipeline stage</Text>
      <Choices items={stages} value={selected.stage} choose={stage => void execute(() => enquiryApi.stage(selected.id, stage), true)}/>
      <Field label="New activity or call outcome" value={newActivity} onChange={setNewActivity} placeholder="Customer requested another call"/>
      <Pressable disabled={busy} onPress={() => {
        if (!newActivity.trim()) { setError('Enter an activity note'); return; }
        void (async () => { if (await execute(() => enquiryApi.addActivity(selected.id, newActivity.trim()), true)) setNewActivity(''); })();
      }} style={styles.primary}><Text style={styles.primaryText}>Add activity</Text></Pressable>
      <Field label="Schedule follow-up (YYYY-MM-DD)" value={newFollowUp} onChange={setNewFollowUp}/>
      <Pressable disabled={busy} onPress={() => {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(newFollowUp)) { setError('Use YYYY-MM-DD for follow-up'); return; }
        void (async () => { if (await execute(() => enquiryApi.followUp(selected.id, newFollowUp), true)) setNewFollowUp(''); })();
      }} style={styles.light}><Text style={styles.lightText}>Save follow-up</Text></Pressable>
      <Text style={[styles.cardTitle, { marginTop: 23 }]}>Activity history</Text>
      {events.map(e => <Text key={e.id} style={styles.history}>• {e.message}{e.createdAt ? ' · ' + new Date(e.createdAt).toLocaleString() : ''}</Text>)}
      {!events.length && <Text style={styles.small}>No activity history yet</Text>}
    </View> : <>
      <Field label="Search" value={query} onChange={setQuery} placeholder="Customer, phone, branch, model"/>
      {visible.map(e => <Pressable key={e.id} style={styles.card} onPress={() => { setAdding(false); setSelectedId(e.id); }}>
        <View style={styles.listTop}><Text style={styles.name}>{e.customerName}</Text><Text style={styles.stage}>{e.stage}</Text></View>
        <Text style={styles.small}>{e.phone} · {e.branch} · {e.salesperson}</Text>
        <Text style={styles.small}>{e.interestedModel}</Text>
        <Text style={styles.link}>Follow-up: {e.followUpDate || 'Not scheduled'} →</Text>
      </Pressable>)}
      {!visible.length && !busy && <Text style={styles.small}>No enquiries found. Create a test enquiry to get started.</Text>}
    </>}
  </View>;
}
const styles = StyleSheet.create({
  root: { paddingBottom: 30 }, title: { fontSize: 23, fontWeight: '800', color: ink },
  description: { fontSize: 12, color: muted, marginTop: 6, marginBottom: 15 },
  warning: { padding: 13, backgroundColor: '#FFF0D6', borderRadius: 10, marginBottom: 15 },
  warningText: { fontSize: 11, color: '#845400', fontWeight: '700' },
  error: { padding: 12, backgroundColor: '#FDE8E8', borderRadius: 8, marginBottom: 10 },
  errorText: { color: '#B42318', fontSize: 12, fontWeight: '700' },
  toolbar: { flexDirection: 'row', alignItems: 'center', gap: 9, marginVertical: 10, flexWrap: 'wrap' },
  label: { fontSize: 12, fontWeight: '700', color: ink, marginTop: 10, marginBottom: 8 },
  card: { padding: 17, backgroundColor: '#FFFFFF', borderWidth: 1, borderColor: border, borderRadius: 13, marginBottom: 10 },
  cardTitle: { fontSize: 18, fontWeight: '800', color: ink, marginBottom: 9 },
  field: { marginBottom: 10 }, input: { padding: 12, backgroundColor: '#FAFCFC', borderWidth: 1, borderColor: border, borderRadius: 9, fontSize: 13, color: ink },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 9 },
  chip: { paddingHorizontal: 11, paddingVertical: 8, backgroundColor: '#FFF', borderColor: border, borderWidth: 1, borderRadius: 9 },
  chipOn: { backgroundColor: teal, borderColor: teal }, chipText: { fontSize: 11, color: ink, fontWeight: '600' }, chipTextOn: { color: '#FFF' },
  primary: { backgroundColor: teal, borderRadius: 9, paddingHorizontal: 14, paddingVertical: 12, alignSelf: 'flex-start' },
  primaryText: { color: '#FFF', fontSize: 12, fontWeight: '800' },
  light: { backgroundColor: '#E4F3EE', borderRadius: 9, paddingHorizontal: 14, paddingVertical: 12, alignSelf: 'flex-start' },
  lightText: { color: teal, fontSize: 12, fontWeight: '800' },
  actions: { flexDirection: 'row', gap: 9, marginTop: 10 }, link: { color: teal, fontSize: 12, fontWeight: '700', marginTop: 10, marginBottom: 8 },
  listTop: { flexDirection: 'row', justifyContent: 'space-between', gap: 8, marginBottom: 7 },
  name: { color: ink, fontSize: 15, fontWeight: '800' }, stage: { color: teal, fontSize: 11, fontWeight: '800' },
  small: { fontSize: 12, color: muted, marginTop: 6 }, info: { fontSize: 12, color: ink, marginVertical: 5 },
  history: { fontSize: 12, color: muted, paddingVertical: 7, borderBottomWidth: 1, borderBottomColor: border },
});
