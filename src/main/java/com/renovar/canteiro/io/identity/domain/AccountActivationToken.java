package com.renovar.canteiro.io.identity.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class AccountActivationToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private Instant consumedAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AccountActivationToken(
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

    public static AccountActivationToken create(UUID userId, String tokenHash, Instant expiresAt) {
        return new AccountActivationToken(null, userId, tokenHash, expiresAt, null, null, null);
    }

    public static AccountActivationToken rehydrate(
            UUID id,
            UUID userId,
            String tokenHash,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new AccountActivationToken(id, userId, tokenHash, expiresAt, consumedAt, createdAt, updatedAt);
    }

    public void consume(Instant currentInstant) {
        if (consumedAt != null) {
            throw new IllegalStateException("Activation token has already been consumed");
        }
        if (!expiresAt.isAfter(currentInstant)) {
            throw new IllegalStateException("Activation token has expired");
        }
        consumedAt = currentInstant;
    }
}
