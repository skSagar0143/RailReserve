package com.railreserve.train;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class TrainSearchServiceTest {
    private JdbcTemplate jdbc;
    private TrainSearchService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        service = new TrainSearchService(jdbc);
    }

    @Test
    void rejectsSameDepartureAndDestination() {
        assertThrows(IllegalArgumentException.class,
            () -> service.search("NDLS", "ndls", LocalDate.now().plusDays(1)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMalformedStationCode() {
        assertThrows(IllegalArgumentException.class,
            () -> service.search("New Delhi", "BCT", LocalDate.now().plusDays(1)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsPastJourneyDate() {
        assertThrows(IllegalArgumentException.class,
            () -> service.search("NDLS", "BCT", LocalDate.now().minusDays(1)));
        verifyNoInteractions(jdbc);
    }
}
