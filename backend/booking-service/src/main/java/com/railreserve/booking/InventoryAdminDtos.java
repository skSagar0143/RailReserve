package com.railreserve.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class InventoryAdminDtos {
    private InventoryAdminDtos() {}
    public record CreateSeatRequest(
        @NotNull @Positive Long trainId,
        @NotNull LocalDate journeyDate,
        @NotBlank @Size(max = 12) @Pattern(regexp = "[A-Za-z0-9-]+") String coachCode,
        @NotBlank @Size(max = 12) @Pattern(regexp = "[A-Za-z0-9-]+") String seatNumber,
        @NotNull @DecimalMin(value = "0.01") BigDecimal fareAmount) {}
    public record CreatedSeat(long inventoryId, String status) {}
}
