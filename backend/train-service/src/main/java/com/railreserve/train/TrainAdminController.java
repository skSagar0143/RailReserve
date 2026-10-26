package com.railreserve.train;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class TrainAdminController {
    private final TrainAdminService service;
    public TrainAdminController(TrainAdminService service) { this.service = service; }

    @PostMapping("/stations")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainAdminDtos.CreatedResource createStation(@Valid @RequestBody TrainAdminDtos.CreateStationRequest request) {
        return service.createStation(request);
    }

    @PostMapping("/trains")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainAdminDtos.CreatedResource createTrain(@Valid @RequestBody TrainAdminDtos.CreateTrainRequest request) {
        return service.createTrain(request);
    }


    @PatchMapping("/stations/{stationId}/status")
    public TrainAdminDtos.CreatedResource updateStationStatus(
            @PathVariable @Positive long stationId, @Valid @RequestBody TrainAdminDtos.UpdateActiveRequest request,
            Authentication authentication) {
        return service.updateStationStatus(stationId, request.active(),
            authentication == null ? "unknown" : authentication.getName());
    }

    @PatchMapping("/trains/{trainId}/status")
    public TrainAdminDtos.CreatedResource updateTrainStatus(
            @PathVariable @Positive long trainId, @Valid @RequestBody TrainAdminDtos.UpdateActiveRequest request,
            Authentication authentication) {
        return service.updateTrainStatus(trainId, request.active(),
            authentication == null ? "unknown" : authentication.getName());
    }

    @PostMapping("/trains/{trainId}/stops")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainAdminDtos.CreatedResource addStop(@PathVariable @Positive long trainId,
        @Valid @RequestBody TrainAdminDtos.CreateStopRequest request) {
        return service.addStop(trainId, request);
    }

    @PostMapping("/trains/{trainId}/service-dates")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainAdminDtos.CreatedResource addServiceDate(@PathVariable @Positive long trainId,
        @Valid @RequestBody TrainAdminDtos.CreateServiceDateRequest request) {
        return service.addServiceDate(trainId, request);
    }

    @PostMapping("/trains/{trainId}/service-dates/{serviceDateId}/cancel")
    public TrainAdminDtos.CreatedResource cancelServiceDate(
            @PathVariable @Positive long trainId, @PathVariable @Positive long serviceDateId,
            Authentication authentication) {
        return service.cancelServiceDate(trainId, serviceDateId,
            authentication == null ? "unknown" : authentication.getName());
    }
}
