import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, TextInputProps, View } from 'react-native';

/** Password box with a Show / Hide toggle. Hidden by default. */
export default function PasswordInput({ style, ...props }: Omit<TextInputProps, 'secureTextEntry'>) {
  const [visible, setVisible] = useState(false);
  return <View style={[s.wrap, style]}>
    <TextInput {...props} style={s.input} secureTextEntry={!visible} autoCapitalize="none" autoCorrect={false} />
    <Pressable onPress={() => setVisible(v => !v)} style={s.toggle} hitSlop={8}
      accessibilityRole="button" accessibilityLabel={visible ? 'Hide password' : 'Show password'}>
      <Text style={s.toggleText}>{visible ? 'Hide' : 'Show'}</Text>
    </Pressable>
  </View>;
}

const s = StyleSheet.create({
  wrap: { flexDirection: 'row', alignItems: 'center', borderRadius: 9, backgroundColor: '#FAFCFC',
    borderWidth: 1, borderColor: '#DCE6E9' },
  input: { flex: 1, padding: 13, fontSize: 14, color: '#18343D', minWidth: 0 },
  toggle: { paddingHorizontal: 14, paddingVertical: 10 },
  toggleText: { color: '#087D72', fontSize: 13, fontWeight: '800' },
});
