package com.railreserve.booking;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingApiService {
    private static final int HOLD_MINUTES = 10;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ObjectMapper objectMapper;

    public BookingApiService(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BookingDtos.BookingResponse create(UUID customerId, String idempotencyKey, BookingDtos.CreateBookingRequest request) {
        if (customerId == null) throw new IllegalArgumentException("Customer identity is required.");
        if (request == null || request.trainId() == null || request.trainId() <= 0 || request.journeyDate() == null
                || request.seatIds() == null || request.seatIds().isEmpty() || request.seatIds().size() > 6
                || request.seatIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("A valid train, journey date, and between 1 and 6 positive seat IDs are required.");
        }
        if (idempotencyKey == null || idempotencyKey.trim().length() < 16 || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must contain 16 to 128 characters.");
        }
        if (request.journeyDate().isBefore(LocalDate.now(ZoneId.of("Asia/Kolkata")))) {
            throw new IllegalArgumentException("Journey date cannot be in the past.");
        }
        List<Long> seatIds = new ArrayList<>(new TreeSet<>(request.seatIds()));
        if (seatIds.size() != request.seatIds().size()) {
            throw new IllegalArgumentException("Seat IDs must be unique.");
        }
        releaseExpiredHolds();
        String requestHash = requestHash(request.trainId(), request.journeyDate(), seatIds);
        ExistingBooking previous = findByIdempotencyKey(customerId, idempotencyKey);
        if (previous != null) {
            if (!previous.requestHash().equals(requestHash)) {
                throw new BookingConflictException("This Idempotency-Key was already used for a different booking request.");
            }
            return response(previous.id(), previous.reference(), previous.status(), previous.trainId(),
                previous.journeyDate(), seatIdsFor(previous.id()), previous.totalAmount(), previous.holdExpiresAt());
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(seatIds.size(), "?"));
        List<SeatRow> seats = jdbc.query(
            "SELECT id, train_id, journey_date, status, fare_amount FROM seat_inventory WHERE id IN (" + placeholders + ") ORDER BY id FOR UPDATE",
            (rs, row) -> new SeatRow(rs.getLong("id"), rs.getLong("train_id"), rs.getDate("journey_date").toLocalDate(),
                rs.getString("status"), rs.getBigDecimal("fare_amount")),
            seatIds.toArray());
        if (seats.size() != seatIds.size()) {
            throw new BookingConflictException("One or more requested seats do not exist.");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SeatRow seat : seats) {
            if (seat.trainId() != request.trainId() || !seat.journeyDate().equals(request.journeyDate())) {
                throw new BookingConflictException("All seats must belong to the requested train and journey date.");
            }
            if (!"AVAILABLE".equals(seat.status())) {
                throw new BookingConflictException("One or more seats are no longer available.");
            }
            if (seat.fareAmount() == null || seat.fareAmount().signum() <= 0) {
                throw new BookingConflictException("A valid server-configured fare is not available for one or more seats.");
            }
            total = total.add(seat.fareAmount());
        }

        UUID bookingId = UUID.randomUUID();
        String reference = "RR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Instant holdExpiresAt = Instant.now().plusSeconds(HOLD_MINUTES * 60L);
        jdbc.update("INSERT INTO bookings(id, booking_reference, customer_id, train_id, journey_date, status, idempotency_key, total_amount, request_hash, hold_expires_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
            uuidBytes(bookingId), reference, uuidBytes(customerId), request.trainId(), java.sql.Date.valueOf(request.journeyDate()),
            "PENDING_PAYMENT", idempotencyKey, total, requestHash, java.sql.Timestamp.from(holdExpiresAt));

        for (SeatRow seat : seats) {
            int updated = jdbc.update("UPDATE seat_inventory SET status='HELD', held_until=?, version=version+1 WHERE id=? AND status='AVAILABLE'",
                java.sql.Timestamp.from(holdExpiresAt), seat.id());
            if (updated != 1) {
                throw new BookingConflictException("Seat availability changed. Please retry with a new Idempotency-Key.");
            }
            jdbc.update("INSERT INTO booking_seats(booking_id, inventory_id) VALUES(?,?)", uuidBytes(bookingId), seat.id());
        }
        writeOutbox(bookingId, "BookingPendingPayment", Map.of(
            "eventId", UUID.randomUUID().toString(),
            "bookingId", bookingId.toString(),
            "bookingReference", reference,
            "customerId", customerId.toString(),
            "trainId", request.trainId(),
            "journeyDate", request.journeyDate().toString(),
            "seatIds", seatIds,
            "totalAmount", total.toPlainString(),
            "holdExpiresAt", holdExpiresAt.toString()));
        return response(bookingId, reference, "PENDING_PAYMENT", request.trainId(), request.journeyDate(), seatIds, total, holdExpiresAt);
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.AvailableSeat> availability(long trainId, LocalDate journeyDate) {
        if (trainId <= 0 || journeyDate == null) {
            throw new IllegalArgumentException("A positive train ID and journey date are required.");
        }
        if (journeyDate.isBefore(LocalDate.now(ZoneId.of("Asia/Kolkata")))) {
            throw new IllegalArgumentException("Journey date cannot be in the past.");
        }
        return jdbc.query("SELECT id, coach_code, seat_number, fare_amount FROM seat_inventory WHERE train_id=? AND journey_date=? AND (status='AVAILABLE' OR (status='HELD' AND held_until<=CURRENT_TIMESTAMP(6))) ORDER BY coach_code, seat_number",
            (rs, row) -> new BookingDtos.AvailableSeat(rs.getLong("id"), rs.getString("coach_code"),
                rs.getString("seat_number"), rs.getBigDecimal("fare_amount")), trainId, java.sql.Date.valueOf(journeyDate));
    }

    @Transactional(readOnly = true)
    public List<BookingDtos.BookingResponse> mine(UUID customerId) {
        if (customerId == null) throw new IllegalArgumentException("Customer identity is required.");
        List<ExistingBooking> records = jdbc.query(
            "SELECT id, booking_reference, status, train_id, journey_date, total_amount, request_hash, created_at, hold_expires_at FROM bookings WHERE customer_id=? ORDER BY created_at DESC LIMIT 100",
            (rs, row) -> new ExistingBooking(uuidFromBytes(rs.getBytes("id")), rs.getString("booking_reference"),
                rs.getString("status"), rs.getLong("train_id"), rs.getDate("journey_date").toLocalDate(),
                rs.getBigDecimal("total_amount"), rs.getString("request_hash"), holdExpiry(rs)), uuidBytes(customerId));
        return records.stream().map(b -> response(b.id(), b.reference(), b.status(), b.trainId(), b.journeyDate(),
            seatIdsFor(b.id()), b.totalAmount(), b.holdExpiresAt())).toList();
    }

    @Transactional(readOnly = true)
    public BookingDtos.BookingResponse get(UUID customerId, UUID bookingId) {
        if (customerId == null || bookingId == null) throw new IllegalArgumentException("Customer and booking identities are required.");
        ExistingBooking b = findById(bookingId);
        if (b == null) throw new BookingConflictException("Booking was not found.");
        if (!customerId.equals(customerIdFor(bookingId))) throw new AccessDeniedException("Booking belongs to another customer.");
        return response(b.id(), b.reference(), b.status(), b.trainId(), b.journeyDate(),
            seatIdsFor(b.id()), b.totalAmount(), b.holdExpiresAt());
    }

    @Transactional
    public BookingDtos.BookingResponse cancel(UUID customerId, UUID bookingId) {
        if (customerId == null || bookingId == null) throw new IllegalArgumentException("Customer and booking identities are required.");
        ExistingBooking b = findByIdForUpdate(bookingId);
        if (b == null) throw new BookingConflictException("Booking was not found.");
        if (!customerId.equals(customerIdFor(bookingId))) throw new AccessDeniedException("Booking belongs to another customer.");
        if ("CANCELLED".equals(b.status()) || "EXPIRED".equals(b.status())) {
            return response(b.id(), b.reference(), b.status(), b.trainId(), b.journeyDate(),
                seatIdsFor(b.id()), b.totalAmount(), null);
        }
        if (!"PENDING_PAYMENT".equals(b.status())) {
            throw new BookingConflictException("Only bookings awaiting payment can be cancelled through this endpoint.");
        }
        jdbc.update("UPDATE bookings SET status='CANCELLED' WHERE id=?", uuidBytes(bookingId));
        jdbc.update("UPDATE seat_inventory si JOIN booking_seats bs ON bs.inventory_id=si.id SET si.status='AVAILABLE', si.held_until=NULL, si.version=si.version+1 WHERE bs.booking_id=? AND si.status='HELD'",
            uuidBytes(bookingId));
        writeOutbox(bookingId, "BookingCancelled", Map.of("eventId", UUID.randomUUID().toString(),
            "bookingId", bookingId.toString(), "bookingReference", b.reference(), "customerId", customerId.toString()));
        return response(b.id(), b.reference(), "CANCELLED", b.trainId(), b.journeyDate(),
            seatIdsFor(b.id()), b.totalAmount(), null);
    }

    @Scheduled(fixedDelayString = "${railreserve.booking.expiry-poll-ms:30000}")
    @Transactional
    public void expirePendingHolds() {
        expirePendingHoldsInternal();
    }

    private void releaseExpiredHolds() {
        expirePendingHoldsInternal();
    }

    private void expirePendingHoldsInternal() {
        List<ExpiredBooking> expired = jdbc.query(
            "SELECT id, booking_reference, customer_id FROM bookings WHERE status='PENDING_PAYMENT' AND hold_expires_at<=CURRENT_TIMESTAMP(6) ORDER BY hold_expires_at LIMIT 100 FOR UPDATE SKIP LOCKED",
            (rs, row) -> new ExpiredBooking(uuidFromBytes(rs.getBytes("id")), rs.getString("booking_reference"),
                uuidFromBytes(rs.getBytes("customer_id"))));
        for (ExpiredBooking booking : expired) {
            int changed = jdbc.update("UPDATE bookings SET status='EXPIRED' WHERE id=? AND status='PENDING_PAYMENT'",
                uuidBytes(booking.id()));
            if (changed == 1) {
                jdbc.update("UPDATE seat_inventory si JOIN booking_seats bs ON bs.inventory_id=si.id SET si.status='AVAILABLE', si.held_until=NULL, si.version=si.version+1 WHERE bs.booking_id=? AND si.status='HELD'",
                    uuidBytes(booking.id()));
                writeOutbox(booking.id(), "BookingExpired", Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "bookingId", booking.id().toString(),
                    "bookingReference", booking.reference(),
                    "customerId", booking.customerId().toString()));
            }
        }
    }

    private ExistingBooking findByIdempotencyKey(UUID customerId, String key) {
        List<ExistingBooking> rows = jdbc.query(
            "SELECT id, booking_reference, status, train_id, journey_date, total_amount, request_hash, created_at, hold_expires_at FROM bookings WHERE customer_id=? AND idempotency_key=? LIMIT 1 FOR UPDATE",
            bookingMapper(), uuidBytes(customerId), key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ExistingBooking findById(UUID id) {
        List<ExistingBooking> rows = jdbc.query(
            "SELECT id, booking_reference, status, train_id, journey_date, total_amount, request_hash, created_at, hold_expires_at FROM bookings WHERE id=?",
            bookingMapper(), uuidBytes(id));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private ExistingBooking findByIdForUpdate(UUID id) {
        List<ExistingBooking> rows = jdbc.query(
            "SELECT id, booking_reference, status, train_id, journey_date, total_amount, request_hash, created_at, hold_expires_at FROM bookings WHERE id=? FOR UPDATE",
            bookingMapper(), uuidBytes(id));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private UUID customerIdFor(UUID bookingId) {
        List<byte[]> rows = jdbc.query("SELECT customer_id FROM bookings WHERE id=?", (rs, row) -> rs.getBytes(1), uuidBytes(bookingId));
        if (rows.isEmpty()) throw new BookingConflictException("Booking was not found.");
        return uuidFromBytes(rows.getFirst());
    }

    private List<Long> seatIdsFor(UUID bookingId) {
        return jdbc.query("SELECT inventory_id FROM booking_seats WHERE booking_id=? ORDER BY inventory_id",
            (rs, row) -> rs.getLong(1), uuidBytes(bookingId));
    }

    private static Instant holdExpiry(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp("hold_expires_at");
        return timestamp == null ? null : timestamp.toInstant();
    }

    private RowMapper<ExistingBooking> bookingMapper() {
        return (rs, row) -> new ExistingBooking(uuidFromBytes(rs.getBytes("id")), rs.getString("booking_reference"),
            rs.getString("status"), rs.getLong("train_id"), rs.getDate("journey_date").toLocalDate(),
            rs.getBigDecimal("total_amount"), rs.getString("request_hash"), holdExpiry(rs));
    }

    private void writeOutbox(UUID aggregateId, String eventType, Map<String, Object> payload) {
        try {
            UUID eventId = UUID.fromString(String.valueOf(payload.getOrDefault("eventId", UUID.randomUUID().toString())));
            jdbc.update("INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, event_type, payload) VALUES(?,?,?,?,?)",
                uuidBytes(eventId), "Booking", aggregateId.toString(), eventType, objectMapper.writeValueAsString(payload));
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize booking event.", e);
        }
    }

    private BookingDtos.BookingResponse response(UUID id, String reference, String status, Long trainId,
                                                  LocalDate date, List<Long> seats, BigDecimal total, Instant holdUntil) {
        return new BookingDtos.BookingResponse(id.toString(), reference, status, trainId, date, seats, total, holdUntil);
    }

    private static String requestHash(long trainId, LocalDate date, List<Long> seatIds) {
        try {
            String value = trainId + "|" + date + "|" + seatIds;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    private static byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
    }

    private static UUID uuidFromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private record ExpiredBooking(UUID id, String reference, UUID customerId) {}
    private record SeatRow(long id, long trainId, LocalDate journeyDate, String status, BigDecimal fareAmount) {}
    private record ExistingBooking(UUID id, String reference, String status, Long trainId, LocalDate journeyDate,
                                   BigDecimal totalAmount, String requestHash, Instant holdExpiresAt) {}
}
