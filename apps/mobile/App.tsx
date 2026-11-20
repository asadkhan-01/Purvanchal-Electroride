import AdminUsers from './AdminUsers';
import LiveBookings from './LiveBookings';
import SessionGate from './SessionGate';
import { CurrentUser, clearLoginCredentials, setUnauthorizedHandler } from './api/enquiries';
import LiveInventory from './LiveInventory';
import LiveCRM from './LiveCRM';
import { apiConfigured } from './api/enquiries';
import LiveDashboard from './LiveDashboard';
import React, { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import {
  Pressable, SafeAreaView, ScrollView, StyleSheet, Text,
  View, useWindowDimensions,
} from 'react-native';

type Section = 'Overview' | 'Enquiries' | 'Inventory' | 'Sales' | 'Finance' | 'Users';
const sections: Section[] = ['Overview', 'Enquiries', 'Inventory', 'Sales', 'Finance'];
const colors = {
  navy: '#102A35', teal: '#087D72', mint: '#DDF4ED',
  bg: '#F4F7F8', white: '#FFFFFF', text: '#152F38',
  muted: '#647882', line: '#E3EAED', amber: '#A96510',
};
function Label({ children }: { children: React.ReactNode }) {
  return <Text style={styles.sectionTitle}>{children}</Text>;
}
export default function App() {
  const [section, setSection] = useState<Section>('Overview');
  const [bookingEnquiryId, setBookingEnquiryId] = useState<number | null>(null);
  const [session, setSession] = useState<CurrentUser | null>(null);
  const logout = () => { clearLoginCredentials(); setSession(null); setBookingEnquiryId(null); setSection('Overview'); };
  useEffect(() => {
    setUnauthorizedHandler(() => { setSession(null); setSection('Overview'); });
    return () => setUnauthorizedHandler(null);
  }, []);

  const { width } = useWindowDimensions();
  const wide = width >= 850;
  const visibleSections = session?.role === 'ADMIN' ? [...sections, 'Users' as Section] : sections;
  return (
    <SafeAreaView style={styles.root}>
      <StatusBar style="dark" />
      <View style={[styles.shell, wide && styles.wideShell]}>
        {wide && <View style={styles.sidebar}>
          <Text style={styles.sidebarMark}>PE</Text>
          <Text style={styles.sidebarBrand}>PURVANCHAL{'\n'}ELECTRORIDE</Text>
          <Text style={styles.sidebarCaption}>DEALERSHIP ERP</Text>
          <View style={{ height: 34 }} />
          {visibleSections.map(item => <Pressable key={item} onPress={() => setSection(item)} style={[styles.sideLink, section === item && styles.sideLinkActive]}>
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
            {apiConfigured && session && <Pressable onPress={logout} style={{ padding: 10, marginRight: 8 }}><Text style={{ color: colors.teal, fontWeight: '700' }}>Sign out</Text></Pressable>}
            <View style={styles.avatar}><Text style={styles.avatarText}>{session ? session.displayName.slice(0, 2).toUpperCase() : 'PE'}</Text></View>
          </View>
          <ScrollView contentContainerStyle={styles.content}>
            <View style={styles.demoNotice}><Text style={styles.demoText}>DEVELOPMENT PREVIEW · Test data only. Development sign-in is not production-ready. Finance is not built yet.</Text></View>
            {!apiConfigured ? <View style={styles.placeholder}><Text style={styles.placeholderTitle}>API not configured</Text><Text style={styles.placeholderBody}>Set EXPO_PUBLIC_API_URL in apps/mobile/.env.local (see README) and restart Expo.</Text></View>
            : !session ? <SessionGate user={session} onSignIn={setSession} /> : <>
            {section === 'Overview' && <>
              <View style={[styles.hero, wide && { flexDirection: 'row', alignItems: 'center' }]}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.heroEyebrow}>OPERATIONS OVERVIEW</Text>
                  <Text style={styles.heroTitle}>Your dealership, at a glance.</Text>
                  <Text style={styles.heroSub}>Track leads, vehicles, sales and collections across branches.</Text>
                </View>
                <Pressable onPress={() => setSection('Enquiries')} style={styles.heroButton}><Text style={styles.heroButtonText}>View enquiries  →</Text></Pressable>
              </View>
              <LiveDashboard user={session} wide={wide} onOpen={setSection} />
              <Label>Quick access</Label>
              <View style={styles.quickGrid}>{(['Enquiries', 'Inventory', 'Sales', 'Finance'] as Section[]).map(item =>
                <Pressable key={item} onPress={() => setSection(item)} style={[styles.quick, { width: wide ? '23.5%' : '48%' }]}>
                  <Text style={styles.quickSymbol}>{item === 'Enquiries' ? '◎' : item === 'Inventory' ? '▣' : item === 'Sales' ? '↗' : '₹'}</Text>
                  <Text style={styles.quickTitle}>{item}</Text><Text style={styles.quickArrow}>→</Text>
                </Pressable>)}</View>
            </>}
            {section === 'Enquiries' && <LiveCRM user={session} onStartBooking={id => { setBookingEnquiryId(id); setSection('Sales'); }} />}
            {section === 'Inventory' && <LiveInventory user={session} />}
            {section === 'Sales' && <LiveBookings user={session} enquiryId={bookingEnquiryId} onClearEnquiry={() => setBookingEnquiryId(null)} />}
            {section === 'Users' && session.role === 'ADMIN' && <AdminUsers />}
            {section === 'Finance' && <View style={styles.placeholder}><Text style={styles.placeholderTitle}>Finance workspace</Text><Text style={styles.placeholderBody}>Payments, lender disbursements, deductions and bank reconciliation will appear here. No real financial data is connected.</Text></View>}
            </>}
            <Text style={styles.footer}>Purvanchal Electroride ERP · Development preview</Text>
          </ScrollView>
          {!wide && <View style={[styles.bottomNav, { flexWrap: 'wrap' }]}>{visibleSections.map(item => <Pressable key={item} onPress={() => setSection(item)} style={[styles.bottomItem, { minWidth: session?.role === 'ADMIN' ? '16%' : '19%' }]}>
            <Text style={[styles.bottomGlyph, section === item && { color: colors.teal }]}>{item === 'Overview' ? '⌂' : item === 'Enquiries' ? '◎' : item === 'Inventory' ? '▣' : item === 'Sales' ? '↗' : item === 'Users' ? '♙' : '₹'}</Text>
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
