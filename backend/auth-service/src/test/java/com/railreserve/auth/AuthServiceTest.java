package com.railreserve.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class AuthServiceTest {
    private AuthUserRepository users;
    private RefreshTokenRepository refreshTokens;
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(AuthUserRepository.class);
        refreshTokens = mock(RefreshTokenRepository.class);
        service = new AuthService(users, refreshTokens, new BCryptPasswordEncoder(12), mock(JwtEncoder.class), 900, 604800);
    }

    @Test
    void registerRejectsDuplicateEmailBeforeSaving() {
        when(users.existsByEmailIgnoreCase("person@example.com")).thenReturn(true);
        assertThrows(AuthConflictException.class, () -> service.register(
            new AuthDtos.RegisterRequest("Person@Example.com", "LongSecurePassword123!", "Person")));
        verify(users, never()).save(any());
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
            service.login(new AuthDtos.LoginRequest("missing@example.com", "password")));
    }
}
