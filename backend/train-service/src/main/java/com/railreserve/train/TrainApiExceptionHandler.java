package com.railreserve.train;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TrainApiExceptionHandler {
    @ExceptionHandler(TrainAdminConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> adminConflict(TrainAdminConflictException ex) {
        return Map.of("error", "conflict", "message", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", "invalid_request", "message", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict() {
        return Map.of("error", "conflict", "message", "The requested train resource conflicts with existing data or references.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validation() {
        return Map.of("error", "invalid_request", "message", "One or more request fields are invalid.");
    }
}
