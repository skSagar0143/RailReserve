package com.railreserve.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
    void refreshRejectsUnknownToken() {
        when(refreshTokens.findByTokenHashForUpdate(anyString())).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
            () -> service.refresh("not-a-valid-refresh-token"));
        verify(refreshTokens).findByTokenHashForUpdate(anyString());
    }

    @Test
    void logoutRevokesTheEntireRefreshTokenFamily() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        String rawToken = "refresh-token-for-test";
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        when(refreshTokens.findByTokenHashForUpdate(hash)).thenReturn(Optional.of(
            new RefreshToken(userId, familyId, hash, Instant.now().plusSeconds(600))));

        service.logout(rawToken);

        verify(refreshTokens).revokeFamily(eq(familyId), any(Instant.class));
    }

    @Test
    void rejectsInvalidJwtTtlConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new AuthService(users, refreshTokens,
            new BCryptPasswordEncoder(), mock(JwtEncoder.class), 30, 604800));
    }

    @Test
    void loginRejectsIncorrectPassword() {
        AuthUser user = new AuthUser("person@example.com", new BCryptPasswordEncoder().encode("CorrectPassword123!"), "Person");
        when(users.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.of(user));
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
            service.login(new AuthDtos.LoginRequest("person@example.com", "WrongPassword123!")));
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () ->
            service.login(new AuthDtos.LoginRequest("missing@example.com", "password")));
    }
    @Test
    void rejectsNullRegistrationAndLoginRequestsBeforeRepositoryWork() {
        assertThrows(IllegalArgumentException.class, () -> service.register(null));
        assertThrows(IllegalArgumentException.class, () -> service.login(null));
        verifyNoInteractions(users, refreshTokens);
    }

    @Test
    void refreshRejectsBlankOrOversizedTokensBeforeRepositoryWork() {
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> service.refresh(" "));
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> service.refresh("x".repeat(513)));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void logoutIgnoresMissingOrOversizedToken() {
        service.logout(null);
        service.logout(" ");
        service.logout("x".repeat(513));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void registrationRejectsMissingEmailBeforeRepositoryWork() {
        assertThrows(IllegalArgumentException.class, () -> service.register(
            new AuthDtos.RegisterRequest(null, "LongSecurePassword123!", "Person")));
        verifyNoInteractions(users, refreshTokens);
    }

}
