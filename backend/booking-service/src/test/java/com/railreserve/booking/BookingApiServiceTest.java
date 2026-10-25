package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class BookingApiServiceTest {
    private JdbcTemplate jdbc;
    private BookingApiService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new BookingApiService(jdbc, mock(NamedParameterJdbcTemplate.class), new ObjectMapper());
    }

    @Test
    void rejectsShortIdempotencyKeyBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "short", request));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNullAndOversizedIdempotencyKeysBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), null, request));
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "x".repeat(129), request));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsJourneyDateInThePastBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().minusDays(1), List.of(1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNullRequestBeforeDatabaseWork() {
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "idempotency-key-12345", null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsEmptySeatListBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMoreThanSixSeatsBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L));
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsNonPositiveTrainIdBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(0L, LocalDate.now().plusDays(1), List.of(1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsDuplicateSeatIdsBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoInteractions(jdbc);
    }
    @Test
    void rejectsInvalidAvailabilityQueryBeforeDatabaseWork() {
        assertThrows(IllegalArgumentException.class, () -> service.availability(0, LocalDate.now().plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.availability(1, null));
        assertThrows(IllegalArgumentException.class, () -> service.availability(1, LocalDate.now().minusDays(1)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingCustomerIdentityForBookingHistoryBeforeDatabaseWork() {
        assertThrows(IllegalArgumentException.class, () -> service.mine(null));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMissingIdentitiesForBookingReadAndCancellationBeforeDatabaseWork() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.get(null, bookingId));
        assertThrows(IllegalArgumentException.class, () -> service.get(customerId, null));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(null, bookingId));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(customerId, null));
        verifyNoInteractions(jdbc);
    }

}
