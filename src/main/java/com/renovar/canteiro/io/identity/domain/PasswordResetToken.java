package com.renovar.canteiro.io.identity.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class PasswordResetToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant consumedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PasswordResetToken(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PasswordResetToken create(UUID userId, String tokenHash, Instant expiresAt) {
        return new PasswordResetToken(null, userId, tokenHash, expiresAt, null, null, null);
    }

    public static PasswordResetToken rehydrate(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PasswordResetToken(id, userId, tokenHash, expiresAt, consumedAt, createdAt, updatedAt);
    }

    public void consume(Instant currentInstant) {
        if (consumedAt != null) {
            throw new IllegalStateException("Password reset token has already been consumed");
        }
        if (!expiresAt.isAfter(currentInstant)) {
            throw new IllegalStateException("Password reset token has expired");
        }
        consumedAt = currentInstant;
    }
}
