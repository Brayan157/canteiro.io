package com.renovar.canteiro.io.platform.catalog.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
public final class PlanFeature {

    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,49}");

    private final UUID id;
    private final String code;
    private final PlanFeatureType type;
    private String name;
    private String description;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PlanFeature(
            UUID id,
            String code,
            PlanFeatureType type,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.code = requireCode(code);
        this.type = requireType(type);
        this.name = requireName(name);
        this.description = normalizeOptional(description);
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlanFeature create(String code, PlanFeatureType type, String name, String description) {
        return new PlanFeature(null, code, type, name, description, true, null, null);
    }

    public static PlanFeature rehydrate(
            UUID id,
            String code,
            PlanFeatureType type,
            String name,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PlanFeature(id, code, type, name, description, active, createdAt, updatedAt);
    }

    public void update(String name, String description) {
        this.name = requireName(name);
        this.description = normalizeOptional(description);
    }

    public void deactivate() {
        active = false;
    }

    private static String requireCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code.trim().toUpperCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("Plan feature code must contain uppercase letters, numbers, or underscores");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static PlanFeatureType requireType(PlanFeatureType type) {
        if (type == null) {
            throw new IllegalArgumentException("Plan feature type is required");
        }
        return type;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Plan feature name is required");
        }
        return name.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
