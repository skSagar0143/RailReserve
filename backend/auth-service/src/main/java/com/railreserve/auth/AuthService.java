package com.railreserve.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long accessTtl;
    private final long refreshTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AuthUserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Value("${security.jwt.access-ttl-seconds:900}") long accessTtl,
                       @Value("${security.jwt.refresh-ttl-seconds:604800}") long refreshTtl) {
        if (accessTtl < 60 || accessTtl > 3600 || refreshTtl < 600 || refreshTtl > 2592000) {
            throw new IllegalArgumentException("JWT access TTL must be 60-3600 seconds and refresh TTL 600-2592000 seconds.");
        }
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new AuthConflictException("An account with this email already exists.");
        }
        AuthUser user = users.save(new AuthUser(email, passwordEncoder.encode(request.password()), request.displayName().trim()));
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        AuthUser user = users.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password."));
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password.");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(String rawToken) {
        String hash = sha256(rawToken);
        RefreshToken stored = refreshTokens.findByTokenHashForUpdate(hash)
            .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));
        Instant now = Instant.now();
        if (!stored.isUsable(now)) {
            throw new BadCredentialsException("Refresh token has expired or was revoked.");
        }
        stored.revoke(now);
        AuthUser user = users.findById(stored.getUserId())
            .filter(AuthUser::isActive)
            .orElseThrow(() -> new BadCredentialsException("Account is unavailable."));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHashForUpdate(sha256(rawToken)).ifPresent(token -> token.revoke(Instant.now()));
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserResponse currentUser(UUID id) {
        AuthUser user = users.findById(id).filter(AuthUser::isActive)
            .orElseThrow(() -> new BadCredentialsException("Account is unavailable."));
        return toUserResponse(user);
    }

    private AuthDtos.TokenResponse issueTokens(AuthUser user) {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(accessTtl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("railreserve-auth")
            .issuedAt(now)
            .expiresAt(expires)
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", user.getRole())
            .audience(List.of("railreserve-api"))
            .id(UUID.randomUUID().toString())
            .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("railreserve-rsa-1").build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        byte[] random = new byte[48];
        secureRandom.nextBytes(random);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshTokens.save(new RefreshToken(user.getId(), sha256(refreshToken), now.plusSeconds(refreshTtl)));
        return new AuthDtos.TokenResponse("Bearer", accessToken, accessTtl, refreshToken, user.getRole());
    }

    private AuthDtos.UserResponse toUserResponse(AuthUser user) {
        return new AuthDtos.UserResponse(user.getId().toString(), user.getEmail(), user.getDisplayName(), user.getRole());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
