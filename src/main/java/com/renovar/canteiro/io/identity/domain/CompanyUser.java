package com.renovar.canteiro.io.identity.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class CompanyUser {

    private final UUID id;
    private final UUID userId;
    private final UUID companyId;
    private final boolean companyOwner;
    private final Instant createdAt;
    private final Instant updatedAt;

    private CompanyUser(
            UUID id,
            UUID userId,
            UUID companyId,
            boolean companyOwner,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.companyId = companyId;
        this.companyOwner = companyOwner;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CompanyUser create(UUID userId, UUID companyId) {
        return new CompanyUser(null, userId, companyId, false, null, null);
    }

    public static CompanyUser createInitialOwner(UUID userId, UUID companyId) {
        return new CompanyUser(null, userId, companyId, true, null, null);
    }

    public static CompanyUser rehydrate(
            UUID id,
            UUID userId,
            UUID companyId,
            Instant createdAt,
            Instant updatedAt
    ) {
        return rehydrate(id, userId, companyId, false, createdAt, updatedAt);
    }

    public static CompanyUser rehydrate(
            UUID id,
            UUID userId,
            UUID companyId,
            boolean companyOwner,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new CompanyUser(id, userId, companyId, companyOwner, createdAt, updatedAt);
    }
}
