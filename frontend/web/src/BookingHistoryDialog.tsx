import React, { useCallback, useEffect, useState } from 'react';

type Booking = { id: string; bookingReference: string; status: string; trainId: number; journeyDate: string; seatIds: number[]; totalAmount: number; holdExpiresAt: string | null };
type Props = { accessToken: string; onClose: () => void };

export default function BookingHistoryDialog({ accessToken, onClose }: Props) {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await fetch(base + '/api/v1/bookings/mine', { headers: { Authorization: 'Bearer ' + accessToken } });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not load your bookings.');
      setBookings(body as Booking[]);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load your bookings.');
    } finally {
      setLoading(false);
    }
  }, [base, accessToken]);

  useEffect(() => { void load(); }, [load]);

  async function cancel(id: string) {
    setError('');
    try {
      const response = await fetch(base + '/api/v1/bookings/' + id + '/cancel', {
        method: 'POST', headers: { Authorization: 'Bearer ' + accessToken }
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not cancel this booking.');
      setBookings(current => current.map(b => b.id === id ? body as Booking : b));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not cancel this booking.');
    }
  }

  return <div className="backdrop" onClick={onClose}>
    <section className="modal glass bookingmodal" role="dialog" aria-modal="true" aria-label="My bookings" onClick={e => e.stopPropagation()}>
      <button className="close" onClick={onClose} aria-label="Close">×</button>
      <h2>My bookings</h2>
      <p>Your bookings are loaded from the authenticated booking service.</p>
      {loading && <p>Loading bookings…</p>}
      {error && <p className="autherror" role="alert">{error}</p>}
      {!loading && !error && bookings.length === 0 && <p>No bookings yet. Search a live train and hold available seats to get started.</p>}
      <div className="historylist">{bookings.map(booking => <article className="historyitem" key={booking.id}>
        <div><strong>{booking.bookingReference}</strong><span>{booking.journeyDate} · Train ID {booking.trainId}</span><span>Seat inventory IDs: {booking.seatIds.join(', ') || '—'}</span></div>
        <div><b>{booking.status}</b><strong>₹{Number(booking.totalAmount).toFixed(2)}</strong>{booking.status === 'PENDING_PAYMENT' && <button className="book" onClick={() => cancel(booking.id)}>Cancel hold</button>}</div>
      </article>)}</div>
      <button className="custom-btn" onClick={() => void load()} disabled={loading}>Refresh bookings</button>
    </section>
  </div>;
}
