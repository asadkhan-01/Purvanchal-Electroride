import React, { useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { CurrentUser, enquiryApi, clearLoginCredentials, setLoginCredentials } from './api/enquiries';
import PasswordInput from './PasswordInput';

type Props = {
  user: CurrentUser | null;
  onSignIn: (user: CurrentUser) => void;
};

export default function SessionGate({ user, onSignIn }: Props) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const signIn = async () => {
    if (busy || !username.trim() || !password) return;
    setBusy(true);
    setError('');
    try {
      setLoginCredentials(username.trim(), password);
      const identity = await enquiryApi.me();
      setPassword('');
      onSignIn(identity);
    } catch (e) {
      clearLoginCredentials();
      setError(e instanceof Error ? e.message : 'Unable to authenticate');
    } finally {
      setBusy(false);
    }
  };

  if (user) return null;
  return <View style={styles.card}>
    <Text style={styles.title}>Sign in to Purvanchal Electroride</Text>
    <Text style={styles.subtitle}>Sign in with your username and password.</Text>
    {error ? <Text style={styles.error}>{error}</Text> : null}
    <Text style={styles.label}>Username</Text>
    <TextInput style={styles.input} value={username} onChangeText={setUsername}
      autoCapitalize="none" autoCorrect={false} editable={!busy} />
    <Text style={styles.label}>Password</Text>
    <PasswordInput value={password} onChangeText={setPassword} editable={!busy}
      onSubmitEditing={() => void signIn()} />
    <Pressable onPress={() => void signIn()} disabled={busy || !username.trim() || !password}
      style={styles.button}>
      {busy ? <ActivityIndicator color="#FFFFFF"/> : <Text style={styles.buttonText}>Sign in</Text>}
    </Pressable>
  </View>;
}

const styles = StyleSheet.create({
  card: { backgroundColor: '#FFFFFF', padding: 24, borderRadius: 14, borderWidth: 1, borderColor: '#DCE6E9' },
  title: { fontSize: 21, fontWeight: '800', color: '#18343D', marginBottom: 8 },
  subtitle: { fontSize: 12, color: '#637780', marginBottom: 15 },
  label: { fontSize: 12, fontWeight: '700', color: '#18343D', marginTop: 12, marginBottom: 6 },
  input: { borderRadius: 9, backgroundColor: '#FAFCFC', borderWidth: 1, borderColor: '#DCE6E9',
    padding: 13, fontSize: 14, color: '#18343D' },
  button: { backgroundColor: '#087D72', padding: 14, borderRadius: 9, alignSelf: 'flex-start', marginTop: 17 },
  buttonText: { color: '#FFFFFF', fontSize: 12, fontWeight: '800' },
  note: { marginTop: 16, color: '#805915', fontSize: 11 },
  error: { color: '#B42318', backgroundColor: '#FDE8E8', padding: 12, borderRadius: 8 },
});
