package com.renovar.canteiro.io.identity.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class PlatformUser {

    private final UUID id;
    private final UUID userId;
    private final PlatformUserRole globalRole;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PlatformUser(
            UUID id,
            UUID userId,
            PlatformUserRole globalRole,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.globalRole = globalRole;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlatformUser create(UUID userId, PlatformUserRole globalRole) {
        return new PlatformUser(null, userId, globalRole, null, null);
    }

    public static PlatformUser rehydrate(
            UUID id,
            UUID userId,
            PlatformUserRole globalRole,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PlatformUser(id, userId, globalRole, createdAt, updatedAt);
    }
}
