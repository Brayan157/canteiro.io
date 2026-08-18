package com.renovar.canteiro.io.contracts.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class ServiceTemplate {

    private final UUID id;
    private final UUID companyId;
    private final String name;
    private final String description;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ServiceTemplate(UUID id, UUID companyId, String name, String description, boolean active, Instant createdAt,
                            Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Service template company is required");
        this.name = requireText(name, "Service template name is required");
        this.description = normalize(description);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ServiceTemplate create(UUID companyId, String name, String description) {
        return new ServiceTemplate(null, companyId, name, description, true, null, null);
    }

    public static ServiceTemplate rehydrate(UUID id, UUID companyId, String name, String description, boolean active,
                                            Instant createdAt, Instant updatedAt) {
        return new ServiceTemplate(id, companyId, name, description, active, createdAt, updatedAt);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
