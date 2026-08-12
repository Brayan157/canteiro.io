package com.renovar.canteiro.io.access.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class UserRole {

    private final UUID id;
    private final UUID userId;
    private final UUID roleId;
    private final UUID companyId;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private UserRole(
            UUID id,
            UUID userId,
            UUID roleId,
            UUID companyId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.userId = requireId(userId, "User");
        this.roleId = requireId(roleId, "Role");
        this.companyId = requireId(companyId, "Company");
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static UserRole create(UUID userId, UUID roleId, UUID companyId) {
        return new UserRole(null, userId, roleId, companyId, true, null, null);
    }

    public static UserRole rehydrate(
            UUID id,
            UUID userId,
            UUID roleId,
            UUID companyId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new UserRole(id, userId, roleId, companyId, active, createdAt, updatedAt);
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    private static UUID requireId(UUID id, String fieldName) {
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " id is required");
        }
        return id;
    }
}
