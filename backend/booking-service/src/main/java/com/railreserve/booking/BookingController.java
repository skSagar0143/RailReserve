package com.railreserve.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private final BookingApiService bookingService;

    public BookingController(BookingApiService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/availability")
    public List<BookingDtos.AvailableSeat> availability(
        @RequestParam @Positive long trainId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate journeyDate) {
        return bookingService.availability(trainId, journeyDate);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDtos.BookingResponse create(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody BookingDtos.CreateBookingRequest request) {
        return bookingService.create(UUID.fromString(jwt.getSubject()), idempotencyKey, request);
    }

    @GetMapping("/mine")
    public List<BookingDtos.BookingResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.mine(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{bookingId}")
    public BookingDtos.BookingResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        return bookingService.get(UUID.fromString(jwt.getSubject()), bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingDtos.BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        return bookingService.cancel(UUID.fromString(jwt.getSubject()), bookingId);
    }
}
