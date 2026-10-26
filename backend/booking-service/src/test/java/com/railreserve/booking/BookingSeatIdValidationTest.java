package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class BookingSeatIdValidationTest {
    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate namedJdbc;
    private BookingApiService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        namedJdbc = mock(NamedParameterJdbcTemplate.class);
        service = new BookingApiService(jdbc, namedJdbc, new ObjectMapper());
    }

    @Test
    void rejectsNullSeatIdBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), Arrays.asList((Long) null));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoDatabaseInteractions();
    }

    @Test
    void rejectsZeroAndNegativeSeatIdsBeforeDatabaseWork() {
        var zero = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(0L));
        var negative = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(-1L));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", zero));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", negative));
        verifyNoDatabaseInteractions();
    }

    @Test
    void rejectsMissingCustomerIdentityBeforeDatabaseWork() {
        var request = validRequest();
        assertThrows(IllegalArgumentException.class,
            () -> service.create(null, "idempotency-key-12345", request));
        verifyNoDatabaseInteractions();
    }

    @Test
    void rejectsInvalidTrainAndEmptySeatListBeforeDatabaseWork() {
        var invalidTrain = new BookingDtos.CreateBookingRequest(0L, LocalDate.now().plusDays(1), List.of(1L));
        var emptySeats = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of());
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", invalidTrain));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", emptySeats));
        verifyNoDatabaseInteractions();
    }

    @Test
    void rejectsMissingOrTooShortIdempotencyKeyBeforeDatabaseWork() {
        UUID customerId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
            () -> service.create(customerId, null, validRequest()));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(customerId, "short", validRequest()));
        verifyNoDatabaseInteractions();
    }

    @Test
    void rejectsPastJourneyDateBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().minusDays(1), List.of(1L));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoDatabaseInteractions();
    }

    private BookingDtos.CreateBookingRequest validRequest() {
        return new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(1L));
    }

    private void verifyNoDatabaseInteractions() {
        verifyNoInteractions(jdbc, namedJdbc);
    }
}
