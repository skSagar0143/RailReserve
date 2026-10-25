package com.railreserve.booking;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BookingApiExceptionHandler {
    @ExceptionHandler(BookingConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(BookingConflictException ex) {
        return Map.of("error", "booking_conflict", "message", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> forbidden() {
        return Map.of("error", "forbidden", "message", "You are not allowed to access this booking.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", "invalid_request", "message", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validation() {
        return Map.of("error", "invalid_request", "message", "One or more request fields are invalid.");
    }
}
