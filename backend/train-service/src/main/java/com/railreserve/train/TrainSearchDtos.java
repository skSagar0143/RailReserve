package com.railreserve.train;

import java.time.LocalDateTime;

public final class TrainSearchDtos {
    private TrainSearchDtos() {}
    public record TrainSearchResult(
        long trainId,
        String trainNumber,
        String trainName,
        String fromCode,
        String fromStation,
        String toCode,
        String toStation,
        LocalDateTime departureAt,
        LocalDateTime arrivalAt,
        long durationMinutes) {}
}
