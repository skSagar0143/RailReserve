package com.railreserve.train;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {
    @Mock
    private StationRepository stationRepository;

    @InjectMocks
    private StationService stationService;

    @Test
    void trimsQueryAndMapsStationToResponseDto() {
        Station station = new Station("NDLS", "New Delhi", "Delhi", true);
        when(stationRepository.searchActive("Delhi")).thenReturn(List.of(station));

        var results = stationService.search("  Delhi  ");

        assertEquals(1, results.size());
        assertEquals("NDLS", results.getFirst().code());
        assertEquals("New Delhi", results.getFirst().name());
        verify(stationRepository).searchActive("Delhi");
    }

    @Test
    void blankQueryListsAllActiveStations() {
        when(stationRepository.searchActive(null)).thenReturn(List.of());

        assertEquals(0, stationService.search("   ").size());
        verify(stationRepository).searchActive(null);
    }
}
