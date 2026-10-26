package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class BookingSeatIdValidationTest {
    private JdbcTemplate jdbc;
    private BookingApiService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new BookingApiService(jdbc, mock(NamedParameterJdbcTemplate.class), new ObjectMapper());
    }

    @Test
    void rejectsNullSeatIdBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), java.util.Arrays.asList((Long) null));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsZeroAndNegativeSeatIdsBeforeDatabaseWork() {
        var zero = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(0L));
        var negative = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(-1L));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", zero));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(UUID.randomUUID(), "idempotency-key-12345", negative));
        verifyNoInteractions(jdbc);
    }
}
