import React, { useState } from 'react';
import {
  ActivityIndicator, Linking, SafeAreaView, ScrollView, StatusBar, StyleSheet, Text,
  TextInput, TouchableOpacity, View
} from 'react-native';

type Theme = 'ocean' | 'forest' | 'sunset';
type Train = {
  trainId: number; trainNumber: string; trainName: string;
  fromStation: string; toStation: string; departureAt: string;
  arrivalAt: string; durationMinutes: number;
};
type Seat = { inventoryId: number; coachCode: string; seatNumber: string; fareAmount: number };
type Booking = {
  id: string; bookingReference: string; status: string; trainId: number;
  journeyDate: string; seatIds: number[]; totalAmount: number; holdExpiresAt: string | null;
};
type Tokens = { accessToken: string; refreshToken: string; role: string };
type PaymentStatus = { bookingStatus: string; paymentStatus: string; checkoutUrl?: string | null };

const themes: Record<Theme, { accent: string; background: string; panel: string }> = {
  ocean: { accent: '#45d6c8', background: '#081626', panel: '#122338' },
  forest: { accent: '#73e0a9', background: '#071b1b', panel: '#102b2b' },
  sunset: { accent: '#ff6f75', background: '#1c1222', panel: '#2b1d31' }
};
const apiBase = process.env.EXPO_PUBLIC_API_BASE_URL || 'http://10.0.2.2:8080';
const duration = (minutes: number) => Math.floor(minutes / 60) + 'h ' + String(minutes % 60).padStart(2, '0') + 'm';
const localTime = (value: string) => value?.slice(11, 16) || '--:--';

export default function App() {
  const [theme, setTheme] = useState<Theme>('ocean');
  const [from, setFrom] = useState('NDLS');
  const [to, setTo] = useState('BCT');
  const [date, setDate] = useState('2026-11-02');
  const [fontScale, setFontScale] = useState(1);
  const [trains, setTrains] = useState<Train[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [accessToken, setAccessToken] = useState('');
  const [refreshToken, setRefreshToken] = useState('');
  const [role, setRole] = useState('');
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  const [showAuth, setShowAuth] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [selectedTrain, setSelectedTrain] = useState<Train | null>(null);
  const [seats, setSeats] = useState<Seat[]>([]);
  const [selectedSeats, setSelectedSeats] = useState<number[]>([]);
  const [booking, setBooking] = useState<Booking | null>(null);
  const [paymentStatus, setPaymentStatus] = useState<PaymentStatus | null>(null);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const colors = themes[theme];

  async function authenticatedFetch(url: string, init: RequestInit = {}) {
    const send = (token: string) => {
      const headers = new Headers(init.headers);
      headers.set('Authorization', 'Bearer ' + token);
      return fetch(url, { ...init, headers });
    };
    const response = await send(accessToken);
    if (response.status !== 401 || !refreshToken) return response;
    try {
      const refreshed = await fetch(apiBase + '/api/v1/auth/refresh', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken })
      });
      if (!refreshed.ok) throw new Error('Refresh token expired.');
      const tokens = await refreshed.json() as Tokens;
      setAccessToken(tokens.accessToken);
      setRefreshToken(tokens.refreshToken);
      setRole(tokens.role);
      return await send(tokens.accessToken);
    } catch {
      setAccessToken('');
      setRefreshToken('');
      setRole('');
      return response;
    }
  }

  async function searchTrains() {
    setLoading(true);
    setMessage('');
    setSelectedTrain(null);
    setSeats([]);
    setBooking(null);
    try {
      const url = apiBase + '/api/v1/trains/search?from=' + encodeURIComponent(from) +
        '&to=' + encodeURIComponent(to) + '&date=' + encodeURIComponent(date);
      const response = await fetch(url);
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Train search failed.');
      setTrains(body as Train[]);
      if ((body as Train[]).length === 0) setMessage('No scheduled trains found. Ask an administrator to configure this route and service date.');
    } catch (error) {
      setTrains([]);
      setMessage(error instanceof Error ? error.message : 'Could not search trains. Check the API address and retry.');
    } finally {
      setLoading(false);
    }
  }

  async function openSeats(train: Train) {
    setSelectedTrain(train);
    setBooking(null);
    setSelectedSeats([]);
    if (!accessToken) {
      setShowAuth(true);
      setMessage('Sign in to view available seats and create a booking hold.');
      return;
    }
    await loadSeats(train, accessToken);
  }

  async function loadSeats(train: Train, token: string) {
    setLoading(true);
    setMessage('');
    try {
      const response = await authenticatedFetch(apiBase + '/api/v1/bookings/availability?trainId=' + train.trainId +
        '&journeyDate=' + encodeURIComponent(date), { headers: { Authorization: 'Bearer ' + token } });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not load seat availability.');
      setSeats(body as Seat[]);
      if ((body as Seat[]).length === 0) setMessage('No available seats are configured for this train and date.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not load seat availability.');
    } finally {
      setLoading(false);
    }
  }

  async function authenticate() {
    setLoading(true);
    setMessage('');
    try {
      const payload = authMode === 'register' ? { email, password, displayName } : { email, password };
      const response = await fetch(apiBase + '/api/v1/auth/' + authMode, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || body.error || 'Authentication failed.');
      const tokens = body as Tokens;
      setAccessToken(tokens.accessToken);
      setRefreshToken(tokens.refreshToken);
      setRole(tokens.role);
      setShowAuth(false);
      setPassword('');
      if (selectedTrain) await loadSeats(selectedTrain, tokens.accessToken);
      else setMessage('Signed in successfully.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Authentication failed.');
    } finally {
      setLoading(false);
    }
  }

  async function logout() {
    try {
      if (refreshToken) await fetch(apiBase + '/api/v1/auth/logout', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken })
      });
    } catch { /* Local session is cleared even if the server cannot be reached. */ }
    setAccessToken('');
    setRefreshToken('');
    setRole('');
    setBookings([]);
    setMessage('Signed out.');
  }

  function toggleSeat(id: number) {
    setSelectedSeats(current => current.includes(id) ? current.filter(x => x !== id) :
      current.length < 6 ? [...current, id] : current);
  }

  async function createBooking() {
    if (!selectedTrain || !accessToken || selectedSeats.length === 0) return;
    setLoading(true);
    setMessage('');
    try {
      const idempotencyKey = 'mobile-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 14);
      const response = await authenticatedFetch(apiBase + '/api/v1/bookings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + accessToken, 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify({ trainId: selectedTrain.trainId, journeyDate: date, seatIds: selectedSeats })
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || body.error || 'Booking request failed.');
      setBooking(body as Booking);
      setPaymentStatus(null);
      setMessage('Seat hold created. Continue to secure checkout to confirm your booking.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Booking request failed.');
    } finally {
      setLoading(false);
    }
  }

  async function startPayment() {
    if (!booking || !accessToken) return;
    setLoading(true); setMessage('');
    try {
      const response = await authenticatedFetch(apiBase + '/api/v1/payments/bookings/' + booking.id + '/checkout', {
        method: 'POST', headers: { Authorization: 'Bearer ' + accessToken }
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || body.error || 'Could not start payment.');
      setPaymentStatus({ ...body, paymentStatus: body.status, bookingStatus: booking.status } as PaymentStatus);
      await Linking.openURL(String(body.checkoutUrl));
      setMessage('Secure checkout opened. After paying, return here and refresh payment status.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Could not start payment.'); }
    finally { setLoading(false); }
  }

  async function refreshPayment() {
    if (!booking || !accessToken) return;
    setLoading(true); setMessage('');
    try {
      const response = await authenticatedFetch(apiBase + '/api/v1/payments/bookings/' + booking.id, {
        headers: { Authorization: 'Bearer ' + accessToken }
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not refresh payment status.');
      setPaymentStatus(body as PaymentStatus);
      setBooking(current => current ? { ...current, status: body.bookingStatus } : current);
      setMessage('Payment: ' + body.paymentStatus + ' · Booking: ' + body.bookingStatus);
      if (body.bookingStatus === 'CONFIRMED') await loadBookings();
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Could not refresh payment status.'); }
    finally { setLoading(false); }
  }

  async function cancelHold() {
    if (!booking || !accessToken) return;
    setLoading(true);
    try {
      const response = await authenticatedFetch(apiBase + '/api/v1/bookings/' + booking.id + '/cancel', {
        method: 'POST', headers: { Authorization: 'Bearer ' + accessToken }
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not cancel the hold.');
      setBooking(body as Booking);
      setSeats(current => current.filter(seat => !booking.seatIds.includes(seat.inventoryId)));
      setSelectedSeats([]);
      setMessage('Seat hold cancelled.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not cancel the hold.');
    } finally {
      setLoading(false);
    }
  }

  async function loadBookings() {
    if (!accessToken) { setShowAuth(true); return; }
    setLoading(true);
    setMessage('');
    try {
      const response = await authenticatedFetch(apiBase + '/api/v1/bookings/mine', { headers: { Authorization: 'Bearer ' + accessToken } });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not load bookings.');
      setBookings(body as Booking[]);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Could not load bookings.');
    } finally {
      setLoading(false);
    }
  }

  return <SafeAreaView style={[styles.safe, { backgroundColor: colors.background }]}>
    <StatusBar barStyle="light-content" />
    <ScrollView contentContainerStyle={styles.container}>
      <View style={styles.toprow}>
        <Text style={[styles.brand, { color: colors.accent, fontSize: 21 * fontScale }]}>RAILRESERVE</Text>
        {accessToken
          ? <TouchableOpacity onPress={logout} style={[styles.smallButton, { borderColor: colors.accent }]}><Text style={{ color: colors.accent }}>Sign out · {role}</Text></TouchableOpacity>
          : <TouchableOpacity onPress={() => setShowAuth(true)} style={[styles.smallButton, { borderColor: colors.accent }]}><Text style={{ color: colors.accent }}>Sign in</Text></TouchableOpacity>}
      </View>
      <Text style={[styles.kicker, { color: colors.accent }]}>YOUR NEXT ADVENTURE STARTS HERE</Text>
      <Text style={[styles.title, { fontSize: 31 * fontScale, lineHeight: 39 * fontScale }]}>Every journey has{'\n'}a better beginning.</Text>
      <Text style={styles.muted}>Search real configured schedules, then sign in to check seats and hold a booking.</Text>

      <View style={[styles.panel, { backgroundColor: colors.panel }]}>
        <Text style={[styles.heading, { fontSize: 16 * fontScale }]}>Plan your journey</Text>
        <Text style={styles.label}>FROM STATION CODE</Text>
        <TextInput accessibilityLabel="Departure station code" autoCapitalize="characters" value={from} onChangeText={setFrom} style={styles.input} />
        <Text style={styles.label}>TO STATION CODE</Text>
        <TextInput accessibilityLabel="Destination station code" autoCapitalize="characters" value={to} onChangeText={setTo} style={styles.input} />
        <Text style={styles.label}>JOURNEY DATE (YYYY-MM-DD)</Text>
        <TextInput accessibilityLabel="Journey date" value={date} onChangeText={setDate} style={styles.input} autoCapitalize="none" />
        <TouchableOpacity style={[styles.button, { backgroundColor: colors.accent }]} onPress={searchTrains} disabled={loading}>
          {loading ? <ActivityIndicator color={colors.background} /> : <Text style={{ color: colors.background, fontWeight: '800' }}>Search trains →</Text>}
        </TouchableOpacity>
      </View>

      {message.length > 0 && <Text accessibilityRole="alert" style={styles.message}>{message}</Text>}
      <View style={styles.rowTitle}><Text style={[styles.heading, { fontSize: 16 * fontScale }]}>Train search results</Text><TouchableOpacity onPress={loadBookings}><Text style={{ color: colors.accent, fontSize: 11 }}>My bookings</Text></TouchableOpacity></View>
      {trains.map(train => <TouchableOpacity key={train.trainId} onPress={() => openSeats(train)} style={[styles.train, { backgroundColor: colors.panel, borderColor: selectedTrain?.trainId === train.trainId ? colors.accent : '#ffffff14' }]}>
        <View style={{ flex: 1 }}>
          <Text style={styles.trainName}>{train.trainName}</Text>
          <Text style={styles.muted}>Train {train.trainNumber} · {train.fromStation} → {train.toStation}</Text>
          <Text style={styles.muted}>{localTime(train.departureAt)} → {localTime(train.arrivalAt)} · {duration(train.durationMinutes)}</Text>
        </View>
        <Text style={{ color: colors.accent, fontWeight: '800' }}>Seats ›</Text>
      </TouchableOpacity>)}

      {selectedTrain && <View style={[styles.panel, { backgroundColor: colors.panel }]}>
        <Text style={[styles.heading, { fontSize: 16 * fontScale }]}>Available seats · {selectedTrain.trainNumber}</Text>
        {!accessToken ? <TouchableOpacity style={[styles.button, { backgroundColor: colors.accent }]} onPress={() => setShowAuth(true)}><Text style={{ color: colors.background, fontWeight: '800' }}>Sign in to continue</Text></TouchableOpacity> :
          booking ? <>
            <Text style={styles.trainName}>Reference: {booking.bookingReference}</Text>
            <Text style={styles.muted}>Status: {booking.status} · Total ₹{Number(booking.totalAmount).toFixed(2)}</Text>
            {booking.holdExpiresAt && <Text style={styles.muted}>Hold expires: {new Date(booking.holdExpiresAt).toLocaleString()}</Text>}
            {paymentStatus && <Text style={styles.muted}>Payment status: {paymentStatus.paymentStatus}</Text>}
            {booking.status === 'PENDING_PAYMENT' && <><TouchableOpacity style={[styles.button, { backgroundColor: colors.accent }]} onPress={startPayment} disabled={loading}><Text style={{ color: colors.background, fontWeight: '800' }}>{loading ? 'Opening checkout…' : 'Pay securely with Razorpay'}</Text></TouchableOpacity><TouchableOpacity style={styles.switchButton} onPress={refreshPayment} disabled={loading}><Text style={{ color: colors.accent }}>Refresh payment status</Text></TouchableOpacity><TouchableOpacity style={styles.switchButton} onPress={cancelHold} disabled={loading}><Text style={styles.muted}>Cancel seat hold</Text></TouchableOpacity></>}
          </> : <>
            <View style={styles.seatGrid}>{seats.map(seat => <TouchableOpacity key={seat.inventoryId} onPress={() => toggleSeat(seat.inventoryId)} style={[styles.seat, { borderColor: selectedSeats.includes(seat.inventoryId) ? colors.accent : '#ffffff24', backgroundColor: selectedSeats.includes(seat.inventoryId) ? '#1d594f' : '#ffffff05' }]}>
              <Text style={styles.seatText}>{seat.coachCode}-{seat.seatNumber}</Text><Text style={styles.muted}>₹{Number(seat.fareAmount).toFixed(2)}</Text>
            </TouchableOpacity>)}</View>
            <Text style={styles.muted}>{selectedSeats.length} seat(s) selected · Total ₹{seats.filter(s => selectedSeats.includes(s.inventoryId)).reduce((sum, seat) => sum + Number(seat.fareAmount), 0).toFixed(2)} · Up to 6 seats</Text>
            <TouchableOpacity style={[styles.button, { backgroundColor: colors.accent }]} onPress={createBooking} disabled={loading || selectedSeats.length === 0}><Text style={{ color: colors.background, fontWeight: '800' }}>{loading ? 'Working…' : 'Hold selected seats'}</Text></TouchableOpacity>
          </>}
      </View>}

      {bookings.length > 0 && <View style={[styles.panel, { backgroundColor: colors.panel }]}><Text style={styles.heading}>My bookings</Text>{bookings.map(item => <View key={item.id} style={styles.bookingRow}><Text style={styles.trainName}>{item.bookingReference} · {item.status}</Text><Text style={styles.muted}>{item.journeyDate} · Train ID {item.trainId} · ₹{Number(item.totalAmount).toFixed(2)}</Text></View>)}</View>}

      <View style={[styles.panel, { backgroundColor: colors.panel }]}>
        <Text style={[styles.heading, { fontSize: 16 * fontScale }]}>Personalize your app</Text>
        <Text style={styles.label}>COLOR THEME</Text>
        <View style={styles.row}>{(['ocean', 'forest', 'sunset'] as Theme[]).map(key => <TouchableOpacity key={key} onPress={() => setTheme(key)} style={[styles.theme, { borderColor: theme === key ? themes[key].accent : '#445' }]}><Text style={styles.muted}>{key}</Text></TouchableOpacity>)}</View>
        <Text style={styles.label}>TEXT SIZE</Text>
        <View style={styles.row}><TouchableOpacity onPress={() => setFontScale(v => Math.max(.9, v - .1))} style={styles.theme}><Text style={styles.muted}>A−</Text></TouchableOpacity><TouchableOpacity onPress={() => setFontScale(v => Math.min(1.3, v + .1))} style={styles.theme}><Text style={styles.muted}>A+</Text></TouchableOpacity></View>
      </View>
      <Text style={styles.muted}>Payments use a hosted Razorpay checkout when the server is configured with credentials. Booking confirmation requires a verified payment webhook.</Text>
    </ScrollView>

    {showAuth && <View style={styles.authOverlay}><View style={[styles.authCard, { backgroundColor: colors.panel }]}>
      <Text style={styles.heading}>{authMode === 'login' ? 'Sign in' : 'Create account'}</Text>
      {authMode === 'register' && <TextInput accessibilityLabel="Display name" placeholder="Display name" placeholderTextColor="#91a4bd" value={displayName} onChangeText={setDisplayName} style={styles.input} />}
      <TextInput accessibilityLabel="Email" keyboardType="email-address" autoCapitalize="none" placeholder="Email" placeholderTextColor="#91a4bd" value={email} onChangeText={setEmail} style={styles.input} />
      <TextInput accessibilityLabel="Password" secureTextEntry placeholder={authMode === 'register' ? 'Password (12+ characters)' : 'Password'} placeholderTextColor="#91a4bd" value={password} onChangeText={setPassword} style={styles.input} maxLength={72} />
      <TouchableOpacity style={[styles.button, { backgroundColor: colors.accent }]} onPress={authenticate} disabled={loading}><Text style={{ color: colors.background, fontWeight: '800' }}>{loading ? 'Please wait…' : authMode === 'login' ? 'Sign in' : 'Register'}</Text></TouchableOpacity>
      <TouchableOpacity onPress={() => setAuthMode(mode => mode === 'login' ? 'register' : 'login')} style={styles.switchButton}><Text style={{ color: colors.accent }}>{authMode === 'login' ? 'Need an account? Register' : 'Already registered? Sign in'}</Text></TouchableOpacity>
      <TouchableOpacity onPress={() => setShowAuth(false)} style={styles.switchButton}><Text style={styles.muted}>Close</Text></TouchableOpacity>
    </View></View>}
  </SafeAreaView>;
}

const styles = StyleSheet.create({
  safe: { flex: 1 },
  container: { padding: 20, paddingBottom: 44 },
  toprow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 },
  brand: { fontWeight: '900', letterSpacing: 1 },
  smallButton: { borderWidth: 1, borderRadius: 9, paddingHorizontal: 10, paddingVertical: 8 },
  kicker: { fontSize: 9, letterSpacing: 1.5, fontWeight: '800' },
  title: { fontWeight: '800', color: '#f1f6ff', marginVertical: 15 },
  muted: { fontSize: 11, color: '#91a4bd', lineHeight: 18 },
  panel: { borderColor: '#ffffff18', borderWidth: 1, borderRadius: 18, padding: 16, marginVertical: 16 },
  heading: { fontSize: 16, fontWeight: '700', color: '#edf4ff', marginBottom: 12 },
  label: { fontSize: 9, letterSpacing: 1.1, color: '#9aacc4', fontWeight: '700', marginTop: 10, marginBottom: 7 },
  input: { minHeight: 44, borderWidth: 1, borderColor: '#ffffff1c', borderRadius: 10, paddingHorizontal: 12, color: '#f0f5ff', fontSize: 12, backgroundColor: '#06111c88', marginBottom: 8 },
  button: { minHeight: 46, alignItems: 'center', justifyContent: 'center', borderRadius: 10, marginTop: 12, paddingHorizontal: 12 },
  train: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderRadius: 14, padding: 13, marginBottom: 9, gap: 10 },
  trainName: { fontSize: 12, fontWeight: '700', color: '#eef4ff', marginBottom: 5 },
  row: { flexDirection: 'row', gap: 8, flexWrap: 'wrap' },
  rowTitle: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  theme: { borderWidth: 1, borderRadius: 9, padding: 10, minWidth: 70, alignItems: 'center' },
  message: { color: '#ffadb1', fontSize: 12, lineHeight: 18, marginVertical: 8 },
  seatGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginVertical: 10 },
  seat: { borderWidth: 1, borderRadius: 9, padding: 10, minWidth: '30%' },
  seatText: { color: '#edf4ff', fontSize: 11, fontWeight: '700', marginBottom: 4 },
  bookingRow: { paddingVertical: 8, borderBottomWidth: 1, borderBottomColor: '#ffffff12' },
  authOverlay: { ...StyleSheet.absoluteFillObject, backgroundColor: '#020812dd', justifyContent: 'center', padding: 20 },
  authCard: { borderWidth: 1, borderColor: '#ffffff20', borderRadius: 18, padding: 18 },
  switchButton: { paddingVertical: 10, alignItems: 'center' }
});
