package com.relic.backend.security;

import com.relic.backend.entity.RefreshToken;
import com.relic.backend.entity.User;
import com.relic.backend.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens are opaque random strings, not JWTs - a JWT can't be
 * revoked before it naturally expires without maintaining a blacklist
 * anyway, which defeats the point of using a stateless token for something
 * that's supposed to be revocable on demand (logout, a compromised device,
 * an admin suspending an account). Only a SHA-256 hash of the token is ever
 * persisted, the same reasoning as hashing a password: a stolen database
 * backup doesn't hand out usable sessions.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.security.refresh-token-expiration-days:7}")
    private long refreshTokenExpirationDays;

    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawToken))
                .expiresAt(Instant.now().plusSeconds(refreshTokenExpirationDays * 24 * 60 * 60))
                .build();

        refreshTokenRepository.save(token);
        return rawToken;
    }

    /**
     * Validates the presented refresh token, revokes it, and issues a new
     * one in its place (rotation) - a stolen refresh token can only be used
     * once before it stops working, which is what limits the damage of a
     * leaked token compared to one that stays valid for its whole lifetime
     * no matter how many times it's used.
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is not recognized"));

        if (existing.isRevoked()) {
            throw new InvalidRefreshTokenException("Refresh token has already been used or revoked");
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        String newRawToken = issue(existing.getUser());
        return new RotationResult(existing.getUser(), newRawToken);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.findByUserIdAndRevokedFalse(userId)
                .forEach(token -> token.setRevoked(true));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory algorithm on every JVM; this branch is
            // unreachable in practice.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record RotationResult(User user, String rawRefreshToken) {
    }

    public static class InvalidRefreshTokenException extends InvalidTokenException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }
}
