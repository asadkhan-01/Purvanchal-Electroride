import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { Activity, CurrentUser, Enquiry, NewEnquiry, Stage, enquiryApi } from './api/enquiries';
import { Salesperson, usersApi } from './api/users';
import { useBranches } from './api/branches';
import { parseDmy, toDmy, toDmyTime } from './dates';

const stages: Stage[] = ['New', 'Contacted', 'Interested', 'Booked', 'Lost'];
const sources = ['Walk-in', 'Phone', 'Website', 'Referral'];
const empty: NewEnquiry = { customerName: '', phone: '', branch: '', salesperson: '',
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
export default function LiveCRM({ user, onStartBooking }: { user: CurrentUser; onStartBooking?: (enquiryId: number) => void }) {
  const [items, setItems] = useState<Enquiry[]>([]);
  const [events, setEvents] = useState<Activity[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [adding, setAdding] = useState(false);
  const branches = useBranches();
  const blankDraft = (): NewEnquiry => ({ ...empty,
    branch: user.branch || branches[0] || '',
    salesperson: user.role === 'SALESPERSON' ? user.displayName : '',
  });
  const [draft, setDraft] = useState<NewEnquiry>(blankDraft);
  useEffect(() => { if (!draft.branch && branches.length) setDraft(d => ({ ...d, branch: d.branch || branches[0] })); }, [branches]);
  const [salespeople, setSalespeople] = useState<Salesperson[]>([]);
  const branchSalespeople = salespeople.filter(p => p.branch === draft.branch).map(p => p.displayName);
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
  useEffect(() => {
    if (user.role === 'SALESPERSON') return;
    usersApi.salespeople().then(setSalespeople).catch(() => setSalespeople([]));
  }, [user.username, user.role]);
  // Keep the chosen salesperson valid for the chosen branch.
  useEffect(() => {
    if (user.role === 'SALESPERSON' || branchSalespeople.includes(draft.salesperson)) return;
    setDraft(d => ({ ...d, salesperson: branchSalespeople[0] ?? '' }));
  }, [draft.branch, salespeople]);

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
    if (!draft.salesperson) {
      setError('Choose a salesperson. Create a salesperson account for this branch in Users first.'); return;
    }
    const followUpIso = draft.followUpDate ? parseDmy(draft.followUpDate) : null;
    if (draft.followUpDate && !followUpIso) {
      setError('Follow-up date must be a real date as DD/MM/YYYY.'); return;
    }
    let created: Enquiry | null = null;
    const ok = await execute(async () => { created = await enquiryApi.create({ ...draft, followUpDate: followUpIso }); });
    if (ok) { setAdding(false); setDraft(blankDraft()); if (created) setSelectedId((created as Enquiry).id); }
  };

  return <View style={styles.root}>
    <Text style={styles.title}>Enquiry management</Text>
    <Text style={styles.description}>{user.displayName} · {user.role} {user.branch ? '· ' + user.branch : '· All branches'}</Text>
    <Text style={styles.description}>Authenticated Spring Boot API · PostgreSQL persistence</Text>
    {error ? <View style={styles.error}><Text style={styles.errorText}>{error}</Text>
      <Text style={styles.small}>Check your connection and try again.</Text></View> : null}
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
      <Text style={styles.label}>Branch</Text><Choices items={user.role === 'ADMIN' ? branches : [user.branch || '']} value={draft.branch} choose={branch => setDraft({ ...draft, branch })}/>
      <Text style={styles.label}>Salesperson</Text>{user.role === 'SALESPERSON' || branchSalespeople.length
        ? <Choices items={user.role === 'SALESPERSON' ? [user.displayName] : branchSalespeople} value={draft.salesperson} choose={salesperson => setDraft({ ...draft, salesperson })}/>
        : <Text style={styles.description}>No salesperson accounts in {draft.branch} yet.</Text>}
      <Text style={styles.label}>Source</Text><Choices items={sources} value={draft.source} choose={source => setDraft({ ...draft, source })}/>
      <Field label="Follow-up date (DD/MM/YYYY)" value={draft.followUpDate || ''} onChange={followUpDate => setDraft({ ...draft, followUpDate: followUpDate || null })}/>
      <Field label="Notes" value={draft.notes || ''} onChange={notes => setDraft({ ...draft, notes })}/>
      <View style={styles.actions}><Pressable disabled={busy} onPress={() => void create()} style={styles.primary}><Text style={styles.primaryText}>Save enquiry</Text></Pressable>
        <Pressable onPress={() => setAdding(false)} style={styles.light}><Text style={styles.lightText}>Cancel</Text></Pressable></View>
    </View> : selected ? <View style={styles.card}>
      <Pressable onPress={() => setSelectedId(null)}><Text style={styles.link}>← Back to enquiries</Text></Pressable>
      <Text style={styles.cardTitle}>{selected.customerName}</Text>
      <Text style={styles.description}>{selected.phone} · {selected.branch} · {selected.salesperson}</Text>
      <Text style={styles.info}>Model: {selected.interestedModel}</Text>
      <Text style={styles.info}>Source: {selected.source}</Text>
      <Text style={styles.info}>Follow-up: {toDmy(selected.followUpDate) || 'Not scheduled'}</Text>
      {onStartBooking && selected.stage !== 'Booked' && selected.stage !== 'Lost' && (
        <Pressable style={styles.primary} onPress={() => onStartBooking(selected.id)}>
          <Text style={styles.primaryText}>Continue to booking →</Text>
        </Pressable>
      )}
      <Text style={styles.label}>Pipeline stage</Text>
      <Choices items={stages} value={selected.stage} choose={stage => void execute(() => enquiryApi.stage(selected.id, stage), true)}/>
      <Field label="New activity or call outcome" value={newActivity} onChange={setNewActivity} placeholder="Customer requested another call"/>
      <Pressable disabled={busy} onPress={() => {
        if (!newActivity.trim()) { setError('Enter an activity note'); return; }
        void (async () => { if (await execute(() => enquiryApi.addActivity(selected.id, newActivity.trim()), true)) setNewActivity(''); })();
      }} style={styles.primary}><Text style={styles.primaryText}>Add activity</Text></Pressable>
      <Field label="Schedule follow-up (DD/MM/YYYY)" value={newFollowUp} onChange={setNewFollowUp}/>
      <Pressable disabled={busy} onPress={() => {
        const iso = parseDmy(newFollowUp);
        if (!iso) { setError('Enter the follow-up date as DD/MM/YYYY'); return; }
        void (async () => { if (await execute(() => enquiryApi.followUp(selected.id, iso), true)) setNewFollowUp(''); })();
      }} style={styles.light}><Text style={styles.lightText}>Save follow-up</Text></Pressable>
      <Text style={[styles.cardTitle, { marginTop: 23 }]}>Activity history</Text>
      {events.map(e => <Text key={e.id} style={styles.history}>• {e.message}{e.createdAt ? ' · ' + toDmyTime(e.createdAt) : ''}</Text>)}
      {!events.length && <Text style={styles.small}>No activity history yet</Text>}
    </View> : <>
      <Field label="Search" value={query} onChange={setQuery} placeholder="Customer, phone, branch, model"/>
      {visible.map(e => <Pressable key={e.id} style={styles.card} onPress={() => { setAdding(false); setSelectedId(e.id); }}>
        <View style={styles.listTop}><Text style={styles.name}>{e.customerName}</Text><Text style={styles.stage}>{e.stage}</Text></View>
        <Text style={styles.small}>{e.phone} · {e.branch} · {e.salesperson}</Text>
        <Text style={styles.small}>{e.interestedModel}</Text>
        <Text style={styles.link}>Follow-up: {toDmy(e.followUpDate) || 'Not scheduled'} →</Text>
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
