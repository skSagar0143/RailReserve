package com.railreserve.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class BookingDtos {
    private BookingDtos() {}

    public record CreateBookingRequest(
        @NotNull @Positive Long trainId,
        @NotNull LocalDate journeyDate,
        @NotEmpty @Size(max = 6) List<@NotNull @Positive Long> seatIds) {}

    public record BookingResponse(
        String id,
        String bookingReference,
        String status,
        Long trainId,
        LocalDate journeyDate,
        List<Long> seatIds,
        BigDecimal totalAmount,
        Instant holdExpiresAt) {}

    public record AvailableSeat(long inventoryId, String coachCode, String seatNumber, BigDecimal fareAmount) {}
}
