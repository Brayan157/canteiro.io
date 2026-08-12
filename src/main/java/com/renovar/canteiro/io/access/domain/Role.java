package com.renovar.canteiro.io.access.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class Role {

    private final UUID id;
    private final UUID companyId;
    private String name;
    private String description;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Role(
            UUID id,
            UUID companyId,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.companyId = requireCompanyId(companyId);
        this.name = requireName(name);
        this.description = description;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Role create(UUID companyId, String name, String description) {
        return new Role(null, companyId, name, description, true, null, null);
    }

    public static Role rehydrate(
            UUID id,
            UUID companyId,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Role(id, companyId, name, description, active, createdAt, updatedAt);
    }

    public void update(String name, String description) {
        this.name = requireName(name);
        this.description = description;
    }

    public void deactivate() {
        active = false;
    }

    private static UUID requireCompanyId(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Role company is required");
        }
        return companyId;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Role name is required");
        }
        return name.trim();
    }
}
