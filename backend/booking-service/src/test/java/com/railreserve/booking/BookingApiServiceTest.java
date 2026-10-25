package com.railreserve.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void rejectsDuplicateSeatIdsBeforeDatabaseWork() {
        var request = new BookingDtos.CreateBookingRequest(1L, LocalDate.now().plusDays(1), List.of(1L, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.create(java.util.UUID.randomUUID(), "idempotency-key-12345", request));
        verifyNoInteractions(jdbc);
    }
}
