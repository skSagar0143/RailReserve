package com.railreserve.booking;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/inventory")
public class InventoryAdminController {
    private final InventoryAdminService service;
    public InventoryAdminController(InventoryAdminService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryAdminDtos.CreatedSeat create(@Valid @RequestBody InventoryAdminDtos.CreateSeatRequest request) {
        return service.create(request);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{inventoryId}/fare")
    public InventoryAdminDtos.FareUpdated updateFare(
            @PathVariable @jakarta.validation.constraints.Positive long inventoryId,
            @Valid @RequestBody InventoryAdminDtos.UpdateFareRequest request,
            Authentication authentication) {
        return service.updateFare(inventoryId, request.fareAmount(),
            authentication == null ? "unknown" : authentication.getName());
    }
}
