import React, { useEffect, useMemo, useState } from 'react';

type Seat = { inventoryId: number; coachCode: string; seatNumber: string; fareAmount: number };
type Booking = { id: string; bookingReference: string; status: string; trainId: number; journeyDate: string; seatIds: number[]; totalAmount: number; holdExpiresAt: string | null };
type Props = { trainId: number; trainName: string; journeyDate: string; accessToken: string; onClose: () => void };

export default function BookingDialog({ trainId, trainName, journeyDate, accessToken, onClose }: Props) {
  const [seats, setSeats] = useState<Seat[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  useEffect(() => {
    let active = true;
    fetch(base + '/api/v1/bookings/availability?trainId=' + trainId + '&journeyDate=' + encodeURIComponent(journeyDate), {
      headers: { Authorization: 'Bearer ' + accessToken }
    }).then(async response => {
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not load seat availability.');
      if (active) setSeats(body as Seat[]);
    }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Could not load seat availability.'); });
    return () => { active = false; };
  }, [base, trainId, journeyDate, accessToken]);

  const total = useMemo(() => seats.filter(s => selected.includes(s.inventoryId)).reduce((sum, seat) => sum + Number(seat.fareAmount), 0), [seats, selected]);

  function toggle(id: number) {
    setSelected(current => current.includes(id) ? current.filter(x => x !== id) : current.length >= 6 ? current : [...current, id]);
  }

  async function createBooking() {
    setError('');
    setLoading(true);
    try {
      const response = await fetch(base + '/api/v1/bookings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + accessToken, 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ trainId, journeyDate, seatIds: selected })
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || body.error || 'Booking request failed.');
      setBooking(body as Booking);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Booking request failed. Please retry.');
    } finally {
      setLoading(false);
    }
  }

  async function cancelHold() {
    if (!booking) return;
    setLoading(true);
    setError('');
    try {
      const response = await fetch(base + '/api/v1/bookings/' + booking.id + '/cancel', {
        method: 'POST',
        headers: { Authorization: 'Bearer ' + accessToken }
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || 'Could not cancel the hold.');
      setBooking(body as Booking);
      setSeats(current => current.filter(s => !booking.seatIds.includes(s.inventoryId)));
      setSelected([]);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not cancel the hold.');
    } finally {
      setLoading(false);
    }
  }

  return <div className="backdrop" onClick={onClose}>
    <section className="modal glass bookingmodal" role="dialog" aria-modal="true" aria-label="Seat selection" onClick={e => e.stopPropagation()}>
      <button className="close" onClick={onClose} aria-label="Close">×</button>
      <h2>{booking ? 'Booking hold' : 'Choose your seats'}</h2>
      <p>{trainName} · {journeyDate}</p>
      {booking ? <>
        <div className="bookingreceipt"><span>Booking reference</span><strong>{booking.bookingReference}</strong><span>Status</span><strong>{booking.status}</strong><span>Seat inventory IDs</span><strong>{booking.seatIds.join(', ')}</strong><span>Server-calculated total</span><strong>₹{Number(booking.totalAmount).toFixed(2)}</strong>{booking.holdExpiresAt && <><span>Hold expires</span><strong>{new Date(booking.holdExpiresAt).toLocaleString()}</strong></>}</div>
        <p className="autherror">Payment is not connected. This booking remains pending payment and the seat hold expires automatically.</p>
        {booking.status === 'PENDING_PAYMENT' && <button className="searchbtn" disabled={loading} onClick={cancelHold}>{loading ? 'Cancelling…' : 'Cancel seat hold'}</button>}
      </> : <>
        {seats.length === 0 && !error && <p>No available seats are configured for this train and date.</p>}
        <div className="seatgrid">{seats.map(seat => <label key={seat.inventoryId} className={'seatoption ' + (selected.includes(seat.inventoryId) ? 'selected' : '')}>
          <input type="checkbox" checked={selected.includes(seat.inventoryId)} onChange={() => toggle(seat.inventoryId)} disabled={loading || (!selected.includes(seat.inventoryId) && selected.length >= 6)} />
          <span>{seat.coachCode}-{seat.seatNumber}</span><small>₹{Number(seat.fareAmount).toFixed(2)}</small>
        </label>)}</div>
        <div className="bookingtotal"><span>{selected.length} seat(s) selected</span><strong>₹{total.toFixed(2)}</strong></div>
        <button className="searchbtn" disabled={loading || selected.length === 0 || selected.some(id => !seats.some(s => s.inventoryId === id))} onClick={createBooking}>{loading ? 'Creating hold…' : 'Hold selected seats'}</button>
      </>}
      {error && <p className="autherror" role="alert">{error}</p>}
    </section>
  </div>;
}
