import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { CurrentUser } from './api/enquiries';
import { NewVehicle, Vehicle, VehicleMovement, inventoryApi } from './api/inventory';
import { useBranches } from './api/branches';
import { allowedAction, canManageBranch, isRegistrationComplete } from './inventoryRules';

const blank: NewVehicle = {
  chassis: '', model: '', variant: '', colour: '', branch: '',
  motorSerial: '', batterySerials: [''], chargerSerial: '', controllerSerial: '',
};
const ink = '#18343D', teal = '#087D72', muted = '#657A83', border = '#DCE6E9';

function Field({ label, value, onChange, hint, secure = false }: {
  label: string; value: string; onChange: (v: string) => void; hint?: string; secure?: boolean;
}) {
  return <View style={s.field}><Text style={s.label}>{label}</Text>
    <TextInput style={s.input} value={value} onChangeText={onChange} placeholder={hint}
      placeholderTextColor="#91A2A9" autoCapitalize={secure ? 'none' : 'sentences'}
      secureTextEntry={secure} autoCorrect={false} /></View>;
}
function Options({ values, value, onSelect }: { values: readonly string[]; value: string; onSelect: (s: string) => void }) {
  return <View style={s.options}>{values.map(v => <Pressable key={v} onPress={() => onSelect(v)}
    style={[s.option, v === value && s.active]}><Text style={[s.optionText, v === value && s.activeText]}>{v}</Text>
  </Pressable>)}</View>;
}
export default function LiveInventory({ user }: { user: CurrentUser }) {
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [movements, setMovements] = useState<VehicleMovement[]>([]);
  const [selectedChassis, setSelectedChassis] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const branches = useBranches();
  const [draft, setDraft] = useState<NewVehicle>(() => ({ ...blank, branch: user.branch || '' }));
  useEffect(() => { if (!draft.branch && branches.length) setDraft(d => ({ ...d, branch: d.branch || branches[0] })); }, [branches]);
  const [destination, setDestination] = useState('');
  const [query, setQuery] = useState('');
  const [branchFilter, setBranchFilter] = useState('All');
  const [statusFilter, setStatusFilter] = useState('All');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const refresh = useCallback(async () => {
    setBusy(true);
    try { setVehicles(await inventoryApi.list()); setError(''); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to load inventory'); }
    finally { setBusy(false); }
  }, []);

  const loadMovements = useCallback(async (chassis: string) => {
    try { setMovements(await inventoryApi.movements(chassis)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to load movements'); }
  }, []);

  useEffect(() => { void refresh(); }, [user.username, refresh]);
  useEffect(() => {
    if (selectedChassis) void loadMovements(selectedChassis);
    else setMovements([]);
  }, [selectedChassis, user, loadMovements]);

  const selected = vehicles.find(v => v.chassis === selectedChassis);
  const filtered = useMemo(() => vehicles.filter(v => {
    const matchesQuery = [v.chassis, v.model, v.variant, v.colour || '', v.motorSerial,
      ...v.batterySerials, v.chargerSerial, v.controllerSerial].join(' ').toLowerCase().includes(query.toLowerCase());
    return matchesQuery && (branchFilter === 'All' || v.branch === branchFilter)
      && (statusFilter === 'All' || v.status === statusFilter);
  }), [vehicles, query, branchFilter, statusFilter]);

  const perform = async (operation: () => Promise<Vehicle>, success: string) => {
    if (busy) return;
    setBusy(true); setError(''); setNotice('');
    try {
      const updated = await operation();
      setVehicles(await inventoryApi.list());
      setSelectedChassis(updated.chassis);
      setMovements(await inventoryApi.movements(updated.chassis));
      setNotice(success);
      setAdding(false);
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Inventory operation failed');
      return false;
    } finally { setBusy(false); }
  };

  const register = async () => {
    if (!user || !canManageBranch(user, draft.branch)) { setError('Registration requires an authorized manager.'); return; }
    if (!isRegistrationComplete(draft)) { setError('Provide chassis, model, variant, motor, charger and controller serials, and at least one battery serial (no repeats).'); return; }
    const success = await perform(() => inventoryApi.register(draft), 'Vehicle registered in PostgreSQL');
    if (success) setDraft({ ...blank, branch: user.branch || branches[0] || '' });
  };

  const action = (type: 'reserve' | 'release' | 'transfer' | 'receive') => {
    if (!user || !selected || !allowedAction(user, selected, type)) {
      setError('You do not have permission or this vehicle is in the wrong state.');
      return;
    }
    if (type === 'transfer') {
      if (!destination.trim() || destination.trim() === selected.branch) {
        setError('Choose a destination different from the source branch.'); return;
      }
      void perform(() => inventoryApi.transfer(selected.chassis, destination.trim()), 'Transfer started');
    } else {
      const actionName = type === 'reserve' ? 'Vehicle reserved'
        : type === 'release' ? 'Reservation released' : 'Transfer received';
      const operation = type === 'reserve'
        ? () => inventoryApi.reserve(selected.chassis)
        : type === 'release'
          ? () => inventoryApi.release(selected.chassis)
          : () => inventoryApi.receive(selected.chassis);
      void perform(operation, actionName);
    }
  };

  return <View style={s.root}>
    <Text style={s.heading}>Vehicle inventory</Text>
    <Text style={s.sub}>{user.displayName} · {user.role} · {user.branch || 'All branches'}</Text>
    <View style={s.actions}>
      <Pressable disabled={busy} onPress={() => void refresh()} style={s.lightButton}><Text style={s.lightText}>Refresh</Text></Pressable>
    </View>
    {!!error && <Text style={s.error}>{error}</Text>}
    {!!notice && <Text style={s.confirmation}>{notice}</Text>}
    {busy && <ActivityIndicator color={teal} />}
    <View style={s.stats}>
      <View style={s.stat}><Text style={s.statValue}>{vehicles.length}</Text><Text style={s.statLabel}>Visible vehicles</Text></View>
      <View style={s.stat}><Text style={s.statValue}>{vehicles.filter(v => v.status === 'AVAILABLE').length}</Text><Text style={s.statLabel}>Available</Text></View>
      <View style={s.stat}><Text style={s.statValue}>{vehicles.filter(v => v.status === 'IN_TRANSIT').length}</Text><Text style={s.statLabel}>In transit</Text></View>
    </View>
    <View style={s.toolbar}><Text style={s.count}>{filtered.length} matching vehicles</Text>
      {(user.role === 'ADMIN' || user.role === 'BRANCH_MANAGER') &&
        <Pressable disabled={busy} style={s.button} onPress={() => { setAdding(true); setSelectedChassis(null); setNotice(''); }}>
          <Text style={s.buttonText}>+ Register vehicle</Text></Pressable>}
    </View>
    {adding ? <View style={s.card}>
      <Text style={s.cardTitle}>Register incoming vehicle</Text>
      <Field label="Chassis / frame number *" value={draft.chassis} onChange={chassis => setDraft({ ...draft, chassis })} />
      <Field label="Model *" value={draft.model} onChange={model => setDraft({ ...draft, model })} />
      <Field label="Variant *" value={draft.variant} onChange={variant => setDraft({ ...draft, variant })} />
      <Field label="Colour" value={draft.colour || ''} onChange={colour => setDraft({ ...draft, colour })} />
      <Text style={s.label}>Receiving branch</Text>
      <Options values={user.role === 'ADMIN' ? branches : [user.branch || '']}
        value={draft.branch} onSelect={branch => setDraft({ ...draft, branch })} />
      <Field label="Motor serial *" value={draft.motorSerial} onChange={motorSerial => setDraft({ ...draft, motorSerial })} />
      {draft.batterySerials.map((serial, i) => <View key={i}>
        <Field label={draft.batterySerials.length > 1 ? `Battery ${i + 1} serial *` : 'Battery serial *'} value={serial}
          onChange={value => setDraft({ ...draft, batterySerials: draft.batterySerials.map((b, j) => j === i ? value : b) })} />
        {draft.batterySerials.length > 1 && <Pressable onPress={() => setDraft({ ...draft, batterySerials: draft.batterySerials.filter((_, j) => j !== i) })}>
          <Text style={{ color: '#B42318', fontSize: 12, fontWeight: '700', marginBottom: 8 }}>Remove battery {i + 1}</Text></Pressable>}
      </View>)}
      {draft.batterySerials.length < 6 && <Pressable onPress={() => setDraft({ ...draft, batterySerials: [...draft.batterySerials, ''] })}>
        <Text style={{ color: '#087D72', fontSize: 12, fontWeight: '700', marginBottom: 10 }}>+ Add another battery</Text></Pressable>}
      <Field label="Charger serial *" value={draft.chargerSerial} onChange={chargerSerial => setDraft({ ...draft, chargerSerial })} />
      <Field label="Controller serial *" value={draft.controllerSerial} onChange={controllerSerial => setDraft({ ...draft, controllerSerial })} />
      <View style={s.actions}>
        <Pressable disabled={busy} onPress={() => void register()} style={s.button}><Text style={s.buttonText}>Save to database</Text></Pressable>
        <Pressable onPress={() => setAdding(false)} style={s.lightButton}><Text style={s.lightText}>Cancel</Text></Pressable>
      </View>
    </View> : selected ? <View style={s.card}>
      <Pressable onPress={() => { setSelectedChassis(null); setNotice(''); }}><Text style={s.link}>← Back to inventory</Text></Pressable>
      <Text style={s.cardTitle}>{selected.model} · {selected.variant}</Text>
      <Text style={s.sub}>{selected.chassis} · {selected.colour || 'Colour not recorded'}</Text>
      <Text style={s.detail}>Source/current branch: {selected.branch}</Text>
      <Text style={s.detail}>Status: {selected.status.replace('_', ' ')}</Text>
      {!!selected.transferDestination && <Text style={s.detail}>Transfer destination: {selected.transferDestination}</Text>}
      <Text style={s.detail}>Motor: {selected.motorSerial}</Text>
      <Text style={s.detail}>Batter{selected.batterySerials.length > 1 ? 'ies' : 'y'}: {selected.batterySerials.join(', ')}</Text>
      <Text style={s.detail}>Charger: {selected.chargerSerial}</Text>
      <Text style={s.detail}>Controller: {selected.controllerSerial}</Text>
      {selected.status === 'AVAILABLE' && canManageBranch(user, selected.branch) &&
        <><Text style={s.label}>Transfer destination</Text>
          <Options values={branches.filter(b => b !== selected.branch)} value={destination} onSelect={setDestination} />
          <View style={s.actions}>
            <Pressable disabled={busy} style={s.button} onPress={() => action('reserve')}><Text style={s.buttonText}>Reserve</Text></Pressable>
            <Pressable disabled={busy} style={s.lightButton} onPress={() => action('transfer')}><Text style={s.lightText}>Start transfer</Text></Pressable>
          </View></>}
      {selected.status === 'RESERVED' && allowedAction(user, selected, 'release') &&
        <Pressable disabled={busy} style={s.lightButton} onPress={() => action('release')}>
          <Text style={s.lightText}>Release reservation</Text></Pressable>}
      {allowedAction(user, selected, 'receive') &&
        <Pressable disabled={busy} style={s.button} onPress={() => action('receive')}>
          <Text style={s.buttonText}>Confirm transfer receipt</Text></Pressable>}
      <Text style={[s.cardTitle, { marginTop: 22 }]}>Movement history</Text>
      {movements.map(m => <Text key={m.id} style={s.history}>
        • {m.event.replace(/_/g, ' ')} · {m.fromBranch || 'Supplier'} → {m.toBranch || '—'} · {m.actorUsername}
      </Text>)}
      {!movements.length && <Text style={s.sub}>No movement records yet</Text>}
    </View> : <>
      <Field label="Search stock" value={query} onChange={setQuery} hint="Chassis, model or component serial" />
      <Text style={s.label}>Branch</Text>
      <Options values={['All', ...branches]} value={branchFilter} onSelect={setBranchFilter} />
      <Text style={s.label}>Status</Text>
      <Options values={['All', 'AVAILABLE', 'RESERVED', 'IN_TRANSIT', 'SOLD']}
        value={statusFilter} onSelect={setStatusFilter} />
      {filtered.map(v => <Pressable key={v.chassis} style={s.card}
        onPress={() => { setSelectedChassis(v.chassis); setDestination(branches.find(b => b !== v.branch) || ''); setNotice(''); }}>
        <View style={s.cardTop}><Text style={s.vehicleName}>{v.model} · {v.variant}</Text>
          <Text style={s.status}>{v.status.replace('_', ' ')}</Text></View>
        <Text style={s.detail}>{v.chassis}</Text><Text style={s.sub}>{v.branch}{v.transferDestination ? ' → ' + v.transferDestination : ''} · {v.colour || 'No colour'}</Text>
        <Text style={s.link}>View vehicle →</Text>
      </Pressable>)}
      {filtered.length === 0 && !busy && <Text style={s.sub}>No inventory records match your search.</Text>}
    </>}
  </View>;
}
const s = StyleSheet.create({
  root: { paddingBottom: 25 }, banner: { backgroundColor: '#FFF1D8', borderRadius: 9, padding: 12, marginBottom: 17 },
  bannerText: { color: '#805915', fontSize: 11, fontWeight: '700' },
  heading: { color: ink, fontSize: 23, fontWeight: '800' }, sub: { color: muted, fontSize: 12, marginTop: 5, marginBottom: 14 },
  error: { color: '#B42318', backgroundColor: '#FDE8E8', padding: 11, borderRadius: 8, marginVertical: 10, fontSize: 12 },
  stats: { flexDirection: 'row', gap: 9, marginVertical: 15 },
  stat: { flex: 1, backgroundColor: '#FFF', borderRadius: 12, padding: 13, borderWidth: 1, borderColor: border },
  statValue: { fontSize: 23, fontWeight: '900', color: ink }, statLabel: { fontSize: 10, color: muted, marginTop: 5 },
  toolbar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 17, gap: 8, flexWrap: 'wrap' },
  count: { color: ink, fontSize: 12, fontWeight: '700' },
  button: { backgroundColor: teal, paddingVertical: 12, paddingHorizontal: 14, borderRadius: 9, alignSelf: 'flex-start' },
  buttonText: { color: '#FFF', fontWeight: '800', fontSize: 12 },
  lightButton: { backgroundColor: '#E4F3EE', paddingVertical: 12, paddingHorizontal: 14, borderRadius: 9, alignSelf: 'flex-start' },
  lightText: { color: teal, fontWeight: '800', fontSize: 12 },
  card: { backgroundColor: '#FFF', borderWidth: 1, borderColor: border, borderRadius: 13, padding: 17, marginBottom: 11 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  cardTitle: { color: ink, fontSize: 18, fontWeight: '800', marginBottom: 10 },
  vehicleName: { color: ink, fontSize: 14, fontWeight: '800', flex: 1 },
  status: { color: teal, fontSize: 11, fontWeight: '800' },
  detail: { color: ink, fontSize: 12, marginVertical: 5 },
  link: { color: teal, fontWeight: '700', fontSize: 12, marginTop: 10, marginBottom: 9 },
  field: { marginBottom: 9 }, label: { color: ink, fontSize: 12, fontWeight: '700', marginBottom: 8, marginTop: 9 },
  input: { borderWidth: 1, borderColor: border, backgroundColor: '#FBFDFD', borderRadius: 9, padding: 12, fontSize: 13, color: ink },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 8 },
  option: { backgroundColor: '#FFF', borderWidth: 1, borderColor: border, paddingHorizontal: 12, paddingVertical: 9, borderRadius: 9 },
  active: { backgroundColor: teal, borderColor: teal }, optionText: { fontSize: 11, color: ink, fontWeight: '600' },
  activeText: { color: '#FFF' }, actions: { flexDirection: 'row', gap: 10, marginVertical: 10, flexWrap: 'wrap' },
  confirmation: { color: teal, fontSize: 12, fontWeight: '700', marginVertical: 9 },
  history: { color: muted, fontSize: 12, paddingVertical: 9, borderBottomWidth: 1, borderBottomColor: border },
});
