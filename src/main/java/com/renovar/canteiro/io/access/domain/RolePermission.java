package com.renovar.canteiro.io.access.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class RolePermission {

    private final UUID id;
    private final UUID roleId;
    private final UUID permissionId;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RolePermission(
            UUID id,
            UUID roleId,
            UUID permissionId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.roleId = requireId(roleId, "Role");
        this.permissionId = requireId(permissionId, "Permission");
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RolePermission create(UUID roleId, UUID permissionId) {
        return new RolePermission(null, roleId, permissionId, true, null, null);
    }

    public static RolePermission rehydrate(
            UUID id,
            UUID roleId,
            UUID permissionId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new RolePermission(id, roleId, permissionId, active, createdAt, updatedAt);
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
