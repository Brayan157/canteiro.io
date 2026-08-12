package com.renovar.canteiro.io.identity.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class CompanyUser {

    private final UUID id;
    private final UUID userId;
    private final UUID companyId;
    private final Instant createdAt;
    private final Instant updatedAt;

    private CompanyUser(UUID id, UUID userId, UUID companyId, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.companyId = companyId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CompanyUser create(UUID userId, UUID companyId) {
        return new CompanyUser(null, userId, companyId, null, null);
    }

    public static CompanyUser rehydrate(
            UUID id,
            UUID userId,
            UUID companyId,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new CompanyUser(id, userId, companyId, createdAt, updatedAt);
    }
}
