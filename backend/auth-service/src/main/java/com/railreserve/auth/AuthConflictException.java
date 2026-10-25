package com.railreserve.auth;

public class AuthConflictException extends RuntimeException {
    public AuthConflictException(String message) { super(message); }
}
