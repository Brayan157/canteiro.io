package com.renovar.canteiro.io.access.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class Permission {

    private final UUID id;
    private final AccessModule module;
    private final AccessAction action;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Permission(
            UUID id,
            AccessModule module,
            AccessAction action,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (module == null || action == null) {
            throw new IllegalArgumentException("Permission module and action are required");
        }
        this.id = id;
        this.module = module;
        this.action = action;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Permission create(AccessModule module, AccessAction action) {
        return new Permission(null, module, action, true, null, null);
    }

    public static Permission rehydrate(
            UUID id,
            AccessModule module,
            AccessAction action,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Permission(id, module, action, active, createdAt, updatedAt);
    }

    public String code() {
        return module.name() + "." + action.name();
    }
}
