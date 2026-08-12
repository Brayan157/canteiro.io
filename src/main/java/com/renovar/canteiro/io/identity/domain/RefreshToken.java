package com.renovar.canteiro.io.identity.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant revokedAt;
    private UUID replacedByTokenId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RefreshToken(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            UUID replacedByTokenId,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.replacedByTokenId = replacedByTokenId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RefreshToken create(UUID userId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(null, userId, tokenHash, expiresAt, null, null, null, null);
    }

    public static RefreshToken rehydrate(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant revokedAt,
            UUID replacedByTokenId,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new RefreshToken(id, userId, tokenHash, expiresAt, revokedAt, replacedByTokenId, createdAt, updatedAt);
    }

    public boolean isUsableAt(Instant currentInstant) {
        return revokedAt == null && expiresAt.isAfter(currentInstant);
    }

    public void revoke(Instant currentInstant) {
        if (revokedAt == null) {
            revokedAt = currentInstant;
        }
    }

    public void replaceWith(UUID nextRefreshTokenId, Instant currentInstant) {
        if (!isUsableAt(currentInstant)) {
            throw new IllegalStateException("Refresh token cannot be replaced");
        }
        revoke(currentInstant);
        replacedByTokenId = nextRefreshTokenId;
    }
}
