package com.railreserve.booking;

public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) { super(message); }
}
