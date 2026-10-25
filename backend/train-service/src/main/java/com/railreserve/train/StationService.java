package com.railreserve.train;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StationService {
    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Transactional(readOnly = true)
    public List<StationResponse> search(String query) {
        String normalized = StringUtils.hasText(query) ? query.trim() : null;
        if (normalized != null && normalized.length() > 80) {
            throw new IllegalArgumentException("Station search query must be 80 characters or fewer");
        }
        return stationRepository.searchActive(normalized).stream()
            .map(station -> new StationResponse(station.getId(), station.getCode(),
                station.getName(), station.getCity()))
            .toList();
    }

    public record StationResponse(Long id, String code, String name, String city) { }
}
