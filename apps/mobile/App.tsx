import CRM from './CRM';
import React, { useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import {
  Pressable, SafeAreaView, ScrollView, StyleSheet, Text,
  TextInput, View, useWindowDimensions,
} from 'react-native';

type Section = 'Overview' | 'Enquiries' | 'Inventory' | 'Sales' | 'Finance';
const sections: Section[] = ['Overview', 'Enquiries', 'Inventory', 'Sales', 'Finance'];
const colors = {
  navy: '#102A35', teal: '#087D72', mint: '#DDF4ED',
  bg: '#F4F7F8', white: '#FFFFFF', text: '#152F38',
  muted: '#647882', line: '#E3EAED', amber: '#A96510',
};
const metrics = [
  { label: 'Sales this month', value: '24', note: 'Vehicles delivered', tone: '#E6F4EF' },
  { label: 'Revenue this month', value: '₹18.6L', note: 'Demo sales total', tone: '#E8EFFB' },
  { label: 'Available stock', value: '68', note: 'Across all branches', tone: '#FDF2DD' },
  { label: 'Open enquiries', value: '39', note: '11 need follow-up', tone: '#F5EAF5' },
];
const enquiries = [
  { initials: 'RK', name: 'Rahul Kumar', detail: 'Electric scooter • Varanasi', status: 'Follow-up due', color: '#A96510' },
  { initials: 'FA', name: 'Farhan Ansari', detail: 'High-speed EV • Ghazipur', status: 'Interested', color: '#087D72' },
  { initials: 'SP', name: 'Sneha Patel', detail: 'Electric scooter • Mau', status: 'New enquiry', color: '#3667B3' },
];
const inventory = [
  { name: 'Electric Scooter — Standard', id: 'CHS-DEMO-001', branch: 'Varanasi', status: 'Available' },
  { name: 'Electric Scooter — Long Range', id: 'CHS-DEMO-002', branch: 'Ghazipur', status: 'Reserved' },
  { name: 'Electric Scooter — Premium', id: 'CHS-DEMO-003', branch: 'Mau', status: 'Available' },
];
function Label({ children }: { children: React.ReactNode }) {
  return <Text style={styles.sectionTitle}>{children}</Text>;
}
export default function App() {
  const [section, setSection] = useState<Section>('Overview');
  const [search, setSearch] = useState('');
  const { width } = useWindowDimensions();
  const wide = width >= 850;
  const shownEnquiries = enquiries.filter(e => (e.name + e.detail + e.status).toLowerCase().includes(search.toLowerCase()));
  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="dark" />
      <View style={[styles.shell, wide && styles.wideShell]}>
        {wide && <View style={styles.sidebar}>
          <Text style={styles.sidebarMark}>PE</Text>
          <Text style={styles.sidebarBrand}>PURVANCHAL{'\n'}ELECTRORIDE</Text>
          <Text style={styles.sidebarCaption}>DEALERSHIP ERP</Text>
          <View style={{ height: 34 }} />
          {sections.map(item => <Pressable key={item} onPress={() => setSection(item)} style={[styles.sideLink, section === item && styles.sideLinkActive]}>
            <Text style={[styles.sideLinkText, section === item && { color: colors.white }]}>{item}</Text>
          </Pressable>)}
          <View style={{ flex: 1 }} />
          <Text style={styles.sidebarFoot}>Development preview · v0.1</Text>
        </View>}
        <View style={styles.main}>
          <View style={styles.topbar}>
            <View style={{ flex: 1 }}>
              {!wide && <Text style={styles.mobileBrand}>PURVANCHAL ELECTRORIDE</Text>}
              <Text style={styles.pageHeading}>{section === 'Overview' ? 'Dashboard' : section}</Text>
              <Text style={styles.subtitle}>Multi-branch dealership management</Text>
            </View>
            <View style={styles.avatar}><Text style={styles.avatarText}>AD</Text></View>
          </View>
          <ScrollView contentContainerStyle={styles.content}>
            <View style={styles.demoNotice}><Text style={styles.demoText}>DEMO MODE  ·  Sample data only — not connected to live records</Text></View>
            {section === 'Overview' && <>
              <View style={[styles.hero, wide && { flexDirection: 'row', alignItems: 'center' }]}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.heroEyebrow}>OPERATIONS OVERVIEW</Text>
                  <Text style={styles.heroTitle}>Your dealership, at a glance.</Text>
                  <Text style={styles.heroSub}>Track leads, vehicles, sales and collections across branches.</Text>
                </View>
                <Pressable onPress={() => setSection('Enquiries')} style={styles.heroButton}><Text style={styles.heroButtonText}>View enquiries  →</Text></Pressable>
              </View>
              <Label>Performance snapshot</Label>
              <View style={styles.metricGrid}>{metrics.map(m => <View key={m.label} style={[styles.metric, { width: wide ? '23.5%' : '48%' }]}>
                <View style={[styles.metricAccent, { backgroundColor: m.tone }]}><Text style={styles.metricDot}>●</Text></View>
                <Text style={styles.metricLabel}>{m.label}</Text><Text style={styles.metricValue}>{m.value}</Text><Text style={styles.metricNote}>{m.note}</Text>
              </View>)}</View>
              <Label>Quick access</Label>
              <View style={styles.quickGrid}>{(['Enquiries', 'Inventory', 'Sales', 'Finance'] as Section[]).map(item =>
                <Pressable key={item} onPress={() => setSection(item)} style={[styles.quick, { width: wide ? '23.5%' : '48%' }]}>
                  <Text style={styles.quickSymbol}>{item === 'Enquiries' ? '◎' : item === 'Inventory' ? '▣' : item === 'Sales' ? '↗' : '₹'}</Text>
                  <Text style={styles.quickTitle}>{item}</Text><Text style={styles.quickArrow}>→</Text>
                </Pressable>)}</View>
              <Label>Recent enquiries</Label>
              {enquiries.map(e => <View key={e.name} style={styles.listRow}><View style={styles.initials}><Text style={styles.initialText}>{e.initials}</Text></View>
                <View style={{ flex: 1 }}><Text style={styles.rowTitle}>{e.name}</Text><Text style={styles.rowSub}>{e.detail}</Text></View>
                <Text style={[styles.status, { color: e.color }]}>{e.status}</Text></View>)}
            </>}
            {section === 'Enquiries' && <CRM />}
            {section === 'Inventory' && <>
              <Text style={styles.intro}>Chassis-level vehicle tracking across branches</Text>
              {inventory.map(v => <View key={v.id} style={styles.vehicleCard}>
                <View style={{ flex: 1 }}><Text style={styles.rowTitle}>{v.name}</Text><Text style={styles.rowSub}>{v.id} · {v.branch}</Text></View>
                <Text style={[styles.status, { color: v.status === 'Available' ? colors.teal : colors.amber }]}>{v.status}</Text>
              </View>)}
            </>}
            {section === 'Sales' && <View style={styles.placeholder}><Text style={styles.placeholderTitle}>Sales workspace</Text><Text style={styles.placeholderBody}>Bookings, vehicle allocation, tax invoices and deliveries will appear here. Demo snapshot: 24 vehicles delivered this month.</Text></View>}
            {section === 'Finance' && <View style={styles.placeholder}><Text style={styles.placeholderTitle}>Finance workspace</Text><Text style={styles.placeholderBody}>Payments, lender disbursements, deductions and bank reconciliation will appear here. No real financial data is connected.</Text></View>}
            <Text style={styles.footer}>Purvanchal Electroride ERP · Development preview</Text>
          </ScrollView>
          {!wide && <View style={styles.bottomNav}>{sections.map(item => <Pressable key={item} onPress={() => setSection(item)} style={styles.bottomItem}>
            <Text style={[styles.bottomGlyph, section === item && { color: colors.teal }]}>{item === 'Overview' ? '⌂' : item === 'Enquiries' ? '◎' : item === 'Inventory' ? '▣' : item === 'Sales' ? '↗' : '₹'}</Text>
            <Text style={[styles.bottomText, section === item && { color: colors.teal, fontWeight: '700' }]}>{item === 'Overview' ? 'Home' : item}</Text>
          </Pressable>)}</View>}
        </View>
      </View>
    </SafeAreaView>
  );
}
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },
  shell: { flex: 1 }, wideShell: { flexDirection: 'row' },
  sidebar: { width: 250, backgroundColor: colors.navy, padding: 24 },
  sidebarMark: { color: '#8DE0C9', fontSize: 31, fontWeight: '900', marginBottom: 12 },
  sidebarBrand: { color: colors.white, fontSize: 16, fontWeight: '900', letterSpacing: 1.1, lineHeight: 22 },
  sidebarCaption: { color: '#9FC2C5', fontSize: 10, marginTop: 8, letterSpacing: 2 },
  sideLink: { padding: 14, borderRadius: 10, marginBottom: 6 },
  sideLinkActive: { backgroundColor: colors.teal },
  sideLinkText: { color: '#BED0D5', fontSize: 14, fontWeight: '600' },
  sidebarFoot: { color: '#93AEB5', fontSize: 11 },
  main: { flex: 1, minWidth: 0 },
  topbar: { paddingHorizontal: 22, paddingVertical: 18, backgroundColor: colors.white, borderBottomWidth: 1, borderBottomColor: colors.line, flexDirection: 'row', alignItems: 'center' },
  mobileBrand: { color: colors.teal, fontSize: 10, letterSpacing: 1.4, fontWeight: '900', marginBottom: 5 },
  pageHeading: { color: colors.text, fontSize: 25, fontWeight: '800' },
  subtitle: { color: colors.muted, fontSize: 12, marginTop: 4 },
  avatar: { backgroundColor: colors.mint, borderRadius: 22, height: 42, width: 42, alignItems: 'center', justifyContent: 'center' },
  avatarText: { color: colors.teal, fontWeight: '800' },
  content: { padding: 20, paddingBottom: 45, maxWidth: 1250, width: '100%', alignSelf: 'center' },
  demoNotice: { backgroundColor: '#FFF4DC', borderRadius: 8, padding: 11, marginBottom: 17 },
  demoText: { color: '#805915', fontSize: 11, fontWeight: '700' },
  hero: { backgroundColor: colors.navy, borderRadius: 18, padding: 24, gap: 18, marginBottom: 24 },
  heroEyebrow: { color: '#7CD9C4', fontSize: 10, letterSpacing: 1.7, fontWeight: '800', marginBottom: 12 },
  heroTitle: { color: colors.white, fontSize: 27, fontWeight: '800', lineHeight: 34, maxWidth: 410 },
  heroSub: { color: '#B8CED3', fontSize: 13, lineHeight: 21, marginTop: 9, maxWidth: 420 },
  heroButton: { backgroundColor: '#2AC7A8', paddingHorizontal: 18, paddingVertical: 13, borderRadius: 10, alignSelf: 'flex-start' },
  heroButtonText: { color: colors.navy, fontWeight: '800', fontSize: 13 },
  sectionTitle: { fontSize: 17, color: colors.text, fontWeight: '800', marginBottom: 13, marginTop: 6 },
  metricGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 12, marginBottom: 24 },
  metric: { backgroundColor: colors.white, borderRadius: 14, padding: 16, borderWidth: 1, borderColor: colors.line, minHeight: 156 },
  metricAccent: { height: 29, width: 29, borderRadius: 8, alignItems: 'center', justifyContent: 'center', marginBottom: 12 },
  metricDot: { color: colors.teal, fontSize: 13 },
  metricLabel: { color: colors.muted, fontSize: 11, fontWeight: '600' },
  metricValue: { color: colors.text, fontSize: 26, fontWeight: '900', marginTop: 5 },
  metricNote: { color: colors.muted, fontSize: 10, marginTop: 4 },
  quickGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 12, marginBottom: 25 },
  quick: { backgroundColor: colors.white, padding: 17, borderRadius: 13, borderWidth: 1, borderColor: colors.line, minHeight: 102 },
  quickSymbol: { color: colors.teal, fontSize: 25, fontWeight: '700' },
  quickTitle: { color: colors.text, fontSize: 13, fontWeight: '700', marginTop: 6 },
  quickArrow: { color: colors.teal, position: 'absolute', right: 16, bottom: 17, fontSize: 17 },
  listRow: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: colors.white, padding: 15, borderWidth: 1, borderColor: colors.line, borderRadius: 12, marginBottom: 9 },
  initials: { height: 39, width: 39, borderRadius: 12, backgroundColor: colors.mint, alignItems: 'center', justifyContent: 'center' },
  initialText: { color: colors.teal, fontWeight: '800', fontSize: 12 },
  rowTitle: { color: colors.text, fontSize: 13, fontWeight: '800' },
  rowSub: { color: colors.muted, fontSize: 11, marginTop: 5 },
  status: { fontSize: 10, fontWeight: '800', textAlign: 'right', maxWidth: 90 },
  intro: { color: colors.muted, fontSize: 13, marginBottom: 17 },
  search: { backgroundColor: colors.white, borderColor: colors.line, borderWidth: 1, borderRadius: 11, padding: 13, marginBottom: 17, fontSize: 14, color: colors.text },
  vehicleCard: { flexDirection: 'row', gap: 10, alignItems: 'center', padding: 17, backgroundColor: colors.white, borderRadius: 12, borderWidth: 1, borderColor: colors.line, marginBottom: 10 },
  placeholder: { backgroundColor: colors.white, padding: 25, borderRadius: 14, borderWidth: 1, borderColor: colors.line },
  placeholderTitle: { color: colors.text, fontSize: 20, fontWeight: '800', marginBottom: 10 },
  placeholderBody: { color: colors.muted, fontSize: 14, lineHeight: 23 },
  empty: { color: colors.muted, padding: 20 },
  footer: { color: '#92A1A9', fontSize: 10, textAlign: 'center', marginTop: 28 },
  bottomNav: { flexDirection: 'row', backgroundColor: colors.white, borderTopWidth: 1, borderTopColor: colors.line, paddingVertical: 9 },
  bottomItem: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  bottomGlyph: { fontSize: 23, color: '#91A2AA', marginBottom: 2 },
  bottomText: { fontSize: 9, color: '#91A2AA' },
});
