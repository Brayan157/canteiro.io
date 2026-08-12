package com.renovar.canteiro.io.platform.catalog.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class PlanFeatureAssignment {

    private final UUID id;
    private final UUID planId;
    private final UUID planFeatureId;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PlanFeatureAssignment(
            UUID id,
            UUID planId,
            UUID planFeatureId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.planId = requireId(planId, "Plan");
        this.planFeatureId = requireId(planFeatureId, "Plan feature");
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlanFeatureAssignment create(UUID planId, UUID planFeatureId) {
        return new PlanFeatureAssignment(null, planId, planFeatureId, true, null, null);
    }

    public static PlanFeatureAssignment rehydrate(
            UUID id,
            UUID planId,
            UUID planFeatureId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PlanFeatureAssignment(id, planId, planFeatureId, active, createdAt, updatedAt);
    }

    public void deactivate() {
        active = false;
    }

    private static UUID requireId(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }
}
