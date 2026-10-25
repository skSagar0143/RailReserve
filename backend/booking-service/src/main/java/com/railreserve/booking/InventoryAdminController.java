package com.railreserve.booking;

import jakarta.validation.Valid;
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
}
