package com.railreserve.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(min = 2, max = 100) String displayName) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank @Size(max = 72) String password) {}
    public record RefreshRequest(@NotBlank @Size(max = 512) String refreshToken) {}
    public record LogoutRequest(@NotBlank @Size(max = 512) String refreshToken) {}
    public record TokenResponse(String tokenType, String accessToken, long expiresInSeconds, String refreshToken, String role) {}
    public record UserResponse(String id, String email, String displayName, String role) {}
}
