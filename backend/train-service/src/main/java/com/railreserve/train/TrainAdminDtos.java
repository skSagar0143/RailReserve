package com.railreserve.train;

import java.time.LocalDate;
import java.time.LocalTime;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class TrainAdminDtos {
    private TrainAdminDtos() {}
    public record CreateStationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{2,10}") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 100) String city) {}
    public record CreateTrainRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{2,20}") String trainNumber,
        @NotBlank @Size(max = 150) String name) {}
    public record CreateStopRequest(
        @NotNull @Positive Long stationId,
        @NotNull @Positive Integer stopSequence,
        LocalTime arrivalTime,
        LocalTime departureTime,
        @NotNull @Min(0) Integer dayOffset) {}
    public record CreateServiceDateRequest(@NotNull LocalDate serviceDate) {}
    public record CreatedResource(long id, String status) {}
}
