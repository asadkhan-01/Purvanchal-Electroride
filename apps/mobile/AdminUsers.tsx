import React, { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { usersApi, UserView, NewUser } from './api/users';
import { userRoles, validateNewUser } from './userRules';
import { useBranches } from './api/branches';
import PasswordInput from './PasswordInput';

const teal = '#087D72', ink = '#18343D', border = '#DCE6E9';
const empty: NewUser = {
  username: '', password: '', displayName: '',
  role: 'SALESPERSON', branch: '',
};

function Field({ label, value, onChange, secure = false }: {
  label: string; value: string; onChange: (value: string) => void; secure?: boolean;
}) {
  return <View style={styles.field}><Text style={styles.label}>{label}</Text>
    {secure ? <PasswordInput value={value} onChangeText={onChange} />
      : <TextInput style={styles.input} value={value} onChangeText={onChange} autoCapitalize="none" autoCorrect={false} />}</View>;
}

export default function AdminUsers() {
  const branchNames = useBranches();
  const [users, setUsers] = useState<UserView[]>([]);
  const [draft, setDraft] = useState<NewUser>({ ...empty });
  useEffect(() => { if (draft.role !== 'ADMIN' && !draft.branch && branchNames.length) setDraft(d => ({ ...d, branch: d.branch || branchNames[0] })); }, [branchNames]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  const refresh = useCallback(async () => {
    setBusy(true);
    try { setUsers(await usersApi.list()); setError(''); }
    catch (e) { setError(e instanceof Error ? e.message : 'Could not load users'); }
    finally { setBusy(false); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);

  const create = async () => {
    if (busy) return;
    const payload = {
      ...draft,
      username: draft.username.trim(),
      displayName: draft.displayName.trim(),
      branch: draft.role === 'ADMIN' ? null : draft.branch,
    };
    const problem = validateNewUser(payload);
    if (problem) { setError(problem); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      const added = await usersApi.create(payload);
      setUsers(await usersApi.list());
      setDraft({ ...empty });
      setNotice('Created ' + added.username + ' as ' + added.role + '. Password is not displayed again.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Could not create user'); }
    finally { setBusy(false); }
  };

  return <View style={styles.root}>
    <Text style={styles.heading}>User administration</Text>
    <Text style={styles.sub}>Create accounts for branch managers and salespeople.</Text>
    {!!error && <Text style={styles.error}>{error}</Text>}
    {!!notice && <Text style={styles.success}>{notice}</Text>}
    {busy && <ActivityIndicator color={teal}/>}
    <View style={styles.card}>
      <Text style={styles.cardTitle}>Create account</Text>
      <Field label="Username *" value={draft.username} onChange={username => setDraft(p => ({ ...p, username }))}/>
      <Field label="Display name *" value={draft.displayName} onChange={displayName => setDraft(p => ({ ...p, displayName }))}/>
      <Field label="Password (12+ characters) *" value={draft.password}
        onChange={password => setDraft(p => ({ ...p, password }))} secure/>
      <Text style={styles.label}>Role</Text>
      <View style={styles.options}>{userRoles.map(role => <Pressable key={role}
        onPress={() => setDraft(p => ({ ...p, role, branch: role === 'ADMIN' ? null : (p.branch || branchNames[0] || '') }))}
        style={[styles.choice, draft.role === role && styles.selected]}>
        <Text style={[styles.choiceText, draft.role === role && styles.selectedText]}>
          {role === 'ADMIN' ? 'Admin' : role === 'BRANCH_MANAGER' ? 'Branch Manager' : 'Salesperson'}
        </Text></Pressable>)}</View>
      {draft.role !== 'ADMIN' && <>
        <Text style={styles.label}>Assigned branch *</Text>
        <View style={styles.options}>{branchNames.map(branch => <Pressable key={branch}
          onPress={() => setDraft(p => ({ ...p, branch }))} style={[styles.choice, draft.branch === branch && styles.selected]}>
          <Text style={[styles.choiceText, draft.branch === branch && styles.selectedText]}>{branch}</Text>
        </Pressable>)}</View>
      </>}
      <Pressable style={styles.primary} disabled={busy} onPress={() => void create()}>
        <Text style={styles.primaryText}>{busy ? 'Saving...' : 'Create user'}</Text>
      </Pressable>
    </View>
    <View style={styles.actions}>
      <Text style={styles.cardTitle}>Accounts ({users.length})</Text>
      <Pressable disabled={busy} onPress={() => void refresh()} style={styles.secondary}>
        <Text style={styles.secondaryText}>Refresh</Text></Pressable>
    </View>
    {users.map(u => <View key={u.id} style={styles.card}>
      <Text style={styles.name}>{u.displayName} · @{u.username}</Text>
      <Text style={styles.sub}>{u.role.replace('_', ' ')} · {u.branch || 'All branches'} · {u.enabled ? 'Enabled' : 'Disabled'}</Text>
    </View>)}
    {!users.length && !busy && <Text style={styles.sub}>No accounts returned by the API.</Text>}
  </View>;
}
const styles = StyleSheet.create({
  root: { paddingBottom: 25 }, heading: { color: ink, fontSize: 23, fontWeight: '800' },
  sub: { color: '#637780', fontSize: 12, marginTop: 7, marginBottom: 12 },
  card: { padding: 17, backgroundColor: '#FFF', borderWidth: 1, borderColor: border, borderRadius: 13, marginBottom: 12 },
  cardTitle: { color: ink, fontSize: 17, fontWeight: '800', marginBottom: 12 },
  name: { color: ink, fontSize: 14, fontWeight: '800' },
  field: { marginBottom: 9 }, label: { color: ink, fontSize: 12, fontWeight: '700', marginBottom: 7, marginTop: 10 },
  input: { color: ink, fontSize: 13, padding: 12, borderWidth: 1, borderColor: border, borderRadius: 9, backgroundColor: '#FBFDFD' },
  options: { flexDirection: 'row', gap: 7, flexWrap: 'wrap', marginBottom: 8 },
  choice: { borderWidth: 1, borderColor: border, backgroundColor: '#FFF', padding: 10, borderRadius: 9 },
  selected: { backgroundColor: teal, borderColor: teal }, choiceText: { fontSize: 11, color: ink },
  selectedText: { color: '#FFF', fontWeight: '800' },
  primary: { backgroundColor: teal, borderRadius: 9, paddingHorizontal: 16, paddingVertical: 13, alignSelf: 'flex-start', marginTop: 10 },
  primaryText: { color: '#FFF', fontWeight: '800', fontSize: 12 },
  secondary: { padding: 10, borderRadius: 9, backgroundColor: '#E4F3EE' },
  secondaryText: { color: teal, fontSize: 12, fontWeight: '800' },
  actions: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginVertical: 12 },
  warning: { padding: 12, borderRadius: 9, backgroundColor: '#FFF1D8', marginVertical: 10 },
  warningText: { fontSize: 11, color: '#805915', fontWeight: '700' },
  error: { color: '#B42318', backgroundColor: '#FDE8E8', borderRadius: 9, padding: 11, marginVertical: 10 },
  success: { color: teal, marginVertical: 10, fontSize: 12, fontWeight: '700' },
});
