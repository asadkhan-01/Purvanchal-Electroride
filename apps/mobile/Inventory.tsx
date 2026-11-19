import React, { useMemo, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

type Status = 'Available' | 'Reserved' | 'In Transit' | 'Sold';
type Vehicle = {
  chassis: string; model: string; variant: string; colour: string;
  branch: string; status: Status; motor: string; battery: string;
  charger: string; controller: string; history: string[];
};
const branches = ['Varanasi', 'Ghazipur', 'Mau'];
const seed: Vehicle[] = [
  { chassis: 'DEMO-CHS-001', model: 'Electric Scooter', variant: 'Standard', colour: 'White', branch: 'Varanasi', status: 'Available', motor: 'MTR-001', battery: 'BAT-001', charger: 'CHR-001', controller: 'CTL-001', history: ['Received at Varanasi'] },
  { chassis: 'DEMO-CHS-002', model: 'Electric Scooter', variant: 'Long Range', colour: 'Blue', branch: 'Ghazipur', status: 'Reserved', motor: 'MTR-002', battery: 'BAT-002', charger: 'CHR-002', controller: 'CTL-002', history: ['Received at Ghazipur', 'Reserved for customer'] },
  { chassis: 'DEMO-CHS-003', model: 'Electric Scooter', variant: 'Premium', colour: 'Black', branch: 'Mau', status: 'Available', motor: 'MTR-003', battery: 'BAT-003', charger: 'CHR-003', controller: 'CTL-003', history: ['Received at Mau'] },
];
const empty = { chassis: '', model: '', variant: '', colour: '', branch: 'Varanasi', motor: '', battery: '', charger: '', controller: '' };
const ink = '#18343D', teal = '#087D72', muted = '#657A83', border = '#DCE6E9';
function Field({ label, value, change, hint }: { label: string; value: string; change: (v: string) => void; hint?: string }) {
  return <View style={s.field}><Text style={s.label}>{label}</Text><TextInput style={s.input} value={value} onChangeText={change} placeholder={hint} placeholderTextColor="#91A2A9" autoCapitalize="characters" /></View>;
}
function Options({ values, value, choose }: { values: string[]; value: string; choose: (v: string) => void }) {
  return <View style={s.options}>{values.map(v => <Pressable key={v} onPress={() => choose(v)} style={[s.option, v === value && s.active]}><Text style={[s.optionText, v === value && s.activeText]}>{v}</Text></Pressable>)}</View>;
}
export default function Inventory() {
  const [vehicles, setVehicles] = useState(seed);
  const [query, setQuery] = useState('');
  const [branchFilter, setBranchFilter] = useState('All');
  const [statusFilter, setStatusFilter] = useState('All');
  const [form, setForm] = useState(empty);
  const [adding, setAdding] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const [destination, setDestination] = useState('Ghazipur');
  const [message, setMessage] = useState('');
  const current = vehicles.find(v => v.chassis === selected);
  const filtered = useMemo(() => vehicles.filter(v => {
    const match = [v.chassis, v.model, v.variant, v.colour, v.motor, v.battery, v.charger, v.controller].join(' ').toLowerCase().includes(query.toLowerCase());
    return match && (branchFilter === 'All' || v.branch === branchFilter) && (statusFilter === 'All' || v.status === statusFilter);
  }), [vehicles, query, branchFilter, statusFilter]);
  const update = (chassis: string, patch: Partial<Vehicle>, event: string) => {
    setVehicles(prev => prev.map(v => v.chassis === chassis ? { ...v, ...patch, history: [...v.history, event] } : v));
    setMessage(event);
  };
  const add = () => {
    const required = [form.chassis, form.model, form.variant, form.motor, form.battery, form.charger, form.controller];
    if (required.some(v => !v.trim())) { Alert.alert('Missing information', 'Complete chassis, model, variant and all four component serial numbers.'); return; }
    const chassis = form.chassis.trim().toUpperCase();
    if (vehicles.some(v => v.chassis.toUpperCase() === chassis)) { Alert.alert('Duplicate chassis', 'This chassis is already registered.'); return; }
    const componentKeys: (keyof typeof empty)[] = ['motor', 'battery', 'charger', 'controller'];
    for (const key of componentKeys) {
      if (vehicles.some(v => String(v[key]).toUpperCase() === form[key].trim().toUpperCase())) {
        Alert.alert('Duplicate component', 'The ' + key + ' serial number is already assigned to another vehicle.'); return;
      }
    }
    setVehicles(prev => [{ ...form, chassis, status: 'Available', history: ['Received at ' + form.branch] }, ...prev]);
    setAdding(false); setForm(empty); setSelected(chassis); setMessage('Vehicle registered');
  };
  const action = (kind: 'reserve' | 'release' | 'transfer' | 'receive') => {
    if (!current) return;
    if (kind === 'reserve' && current.status === 'Available') update(current.chassis, { status: 'Reserved' }, 'Vehicle reserved');
    if (kind === 'release' && current.status === 'Reserved') update(current.chassis, { status: 'Available' }, 'Reservation released');
    if (kind === 'transfer' && current.status === 'Available') {
      if (destination === current.branch) { Alert.alert('Select another branch', 'Destination must differ from the current branch.'); return; }
      update(current.chassis, { status: 'In Transit', branch: destination }, 'Transfer initiated to ' + destination);
    }
    if (kind === 'receive' && current.status === 'In Transit') update(current.chassis, { status: 'Available' }, 'Transfer received at ' + current.branch);
  };
  return <View style={s.root}>
    <View style={s.banner}><Text style={s.bannerText}>DEMO INVENTORY · In-memory records only. Transfer and reservation actions are simulated.</Text></View>
    <Text style={s.heading}>Vehicle inventory</Text><Text style={s.sub}>Individual vehicle tracking with chassis and component serial numbers.</Text>
    <View style={s.stats}><View style={s.stat}><Text style={s.statValue}>{vehicles.length}</Text><Text style={s.statLabel}>Total vehicles</Text></View><View style={s.stat}><Text style={s.statValue}>{vehicles.filter(v => v.status === 'Available').length}</Text><Text style={s.statLabel}>Available</Text></View><View style={s.stat}><Text style={s.statValue}>{vehicles.filter(v => v.status === 'Reserved').length}</Text><Text style={s.statLabel}>Reserved</Text></View></View>
    <View style={s.toolbar}><Text style={s.count}>{filtered.length} vehicles</Text><Pressable style={s.button} onPress={() => { setAdding(true); setSelected(null); setMessage(''); }}><Text style={s.buttonText}>+ Register vehicle</Text></Pressable></View>
    {adding ? <View style={s.card}>
      <Text style={s.cardTitle}>Register incoming vehicle</Text>
      <Field label="Chassis / frame number *" value={form.chassis} change={chassis => setForm({ ...form, chassis })}/>
      <Field label="EV model *" value={form.model} change={model => setForm({ ...form, model })}/>
      <Field label="Variant *" value={form.variant} change={variant => setForm({ ...form, variant })}/>
      <Field label="Colour" value={form.colour} change={colour => setForm({ ...form, colour })}/>
      <Text style={s.label}>Receiving branch</Text><Options values={branches} value={form.branch} choose={branch => setForm({ ...form, branch })}/>
      <Field label="Motor serial number *" value={form.motor} change={motor => setForm({ ...form, motor })}/>
      <Field label="Battery serial number *" value={form.battery} change={battery => setForm({ ...form, battery })}/>
      <Field label="Charger serial number *" value={form.charger} change={charger => setForm({ ...form, charger })}/>
      <Field label="Controller serial number *" value={form.controller} change={controller => setForm({ ...form, controller })}/>
      <View style={s.actions}><Pressable style={s.button} onPress={add}><Text style={s.buttonText}>Save vehicle</Text></Pressable><Pressable style={s.lightButton} onPress={() => setAdding(false)}><Text style={s.lightText}>Cancel</Text></Pressable></View>
    </View> : current ? <View style={s.card}>
      <Pressable onPress={() => { setSelected(null); setMessage(''); }}><Text style={s.link}>← Back to stock</Text></Pressable>
      <Text style={s.cardTitle}>{current.model} · {current.variant}</Text>
      <Text style={s.sub}>{current.chassis} · {current.colour || 'Colour not recorded'}</Text>
      <Text style={s.detail}>Branch: {current.branch}</Text><Text style={s.detail}>Status: {current.status}</Text>
      <Text style={s.detail}>Motor: {current.motor}</Text><Text style={s.detail}>Battery: {current.battery}</Text><Text style={s.detail}>Charger: {current.charger}</Text><Text style={s.detail}>Controller: {current.controller}</Text>
      {message ? <Text style={s.confirmation}>{message}</Text> : null}
      {current.status === 'Available' && <><Text style={s.label}>Transfer destination</Text><Options values={branches.filter(b => b !== current.branch)} value={destination === current.branch ? '' : destination} choose={setDestination}/>
        <View style={s.actions}><Pressable style={s.button} onPress={() => action('reserve')}><Text style={s.buttonText}>Reserve</Text></Pressable><Pressable style={s.lightButton} onPress={() => action('transfer')}><Text style={s.lightText}>Start transfer</Text></Pressable></View></>}
      {current.status === 'Reserved' && <Pressable style={s.lightButton} onPress={() => action('release')}><Text style={s.lightText}>Release reservation</Text></Pressable>}
      {current.status === 'In Transit' && <Pressable style={s.button} onPress={() => action('receive')}><Text style={s.buttonText}>Confirm receipt</Text></Pressable>}
      <Text style={[s.cardTitle, { marginTop: 24 }]}>Movement history</Text>
      {current.history.slice().reverse().map((h, i) => <Text key={i} style={s.history}>• {h}</Text>)}
    </View> : <>
      <Field label="Search stock" value={query} change={setQuery} hint="Chassis, model, component serial"/>
      <Text style={s.label}>Branch</Text><Options values={['All', ...branches]} value={branchFilter} choose={setBranchFilter}/>
      <Text style={s.label}>Status</Text><Options values={['All', 'Available', 'Reserved', 'In Transit', 'Sold']} value={statusFilter} choose={setStatusFilter}/>
      {filtered.map(v => <Pressable key={v.chassis} style={s.card} onPress={() => { setSelected(v.chassis); setDestination(branches.find(b => b !== v.branch) || v.branch); setMessage(''); }}>
        <View style={s.cardTop}><Text style={s.vehicleName}>{v.model} · {v.variant}</Text><Text style={s.status}>{v.status}</Text></View>
        <Text style={s.detail}>{v.chassis}</Text><Text style={s.sub}>{v.branch} · {v.colour}</Text><Text style={s.link}>View vehicle →</Text>
      </Pressable>)}
      {filtered.length === 0 && <Text style={s.sub}>No matching vehicles.</Text>}
    </>}
  </View>;
}
const s = StyleSheet.create({
  root: { paddingBottom: 25 }, banner: { backgroundColor: '#FFF1D8', borderRadius: 9, padding: 12, marginBottom: 17 },
  bannerText: { color: '#805915', fontSize: 11, fontWeight: '700' },
  heading: { color: ink, fontSize: 23, fontWeight: '800' }, sub: { color: muted, fontSize: 12, marginTop: 5, marginBottom: 14 },
  stats: { flexDirection: 'row', gap: 9, marginVertical: 15 }, stat: { flex: 1, backgroundColor: '#FFF', borderRadius: 12, padding: 13, borderWidth: 1, borderColor: border },
  statValue: { fontSize: 23, fontWeight: '900', color: ink }, statLabel: { fontSize: 10, color: muted, marginTop: 5 },
  toolbar: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 17, gap: 8 },
  count: { color: ink, fontSize: 12, fontWeight: '700' }, button: { backgroundColor: teal, paddingVertical: 12, paddingHorizontal: 14, borderRadius: 9, alignSelf: 'flex-start' },
  buttonText: { color: '#FFF', fontWeight: '800', fontSize: 12 }, lightButton: { backgroundColor: '#E4F3EE', paddingVertical: 12, paddingHorizontal: 14, borderRadius: 9, alignSelf: 'flex-start' },
  lightText: { color: teal, fontWeight: '800', fontSize: 12 }, card: { backgroundColor: '#FFF', borderWidth: 1, borderColor: border, borderRadius: 13, padding: 17, marginBottom: 11 },
  cardTop: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 }, cardTitle: { color: ink, fontSize: 18, fontWeight: '800', marginBottom: 10 },
  vehicleName: { color: ink, fontSize: 14, fontWeight: '800', flex: 1 }, status: { color: teal, fontSize: 11, fontWeight: '800' },
  detail: { color: ink, fontSize: 12, marginVertical: 5 }, link: { color: teal, fontWeight: '700', fontSize: 12, marginTop: 10, marginBottom: 9 },
  field: { marginBottom: 9 }, label: { color: ink, fontSize: 12, fontWeight: '700', marginBottom: 8, marginTop: 9 },
  input: { borderWidth: 1, borderColor: border, backgroundColor: '#FBFDFD', borderRadius: 9, padding: 12, fontSize: 13, color: ink },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 8 },
  option: { backgroundColor: '#FFF', borderWidth: 1, borderColor: border, paddingHorizontal: 12, paddingVertical: 9, borderRadius: 9 },
  active: { backgroundColor: teal, borderColor: teal }, optionText: { fontSize: 11, color: ink, fontWeight: '600' }, activeText: { color: '#FFF' },
  actions: { flexDirection: 'row', gap: 10, marginTop: 13, flexWrap: 'wrap' }, confirmation: { color: teal, fontSize: 12, fontWeight: '700', marginTop: 10 },
  history: { color: muted, fontSize: 12, paddingVertical: 7 },
});
