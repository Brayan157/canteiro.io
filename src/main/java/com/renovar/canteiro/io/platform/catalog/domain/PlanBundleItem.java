package com.renovar.canteiro.io.platform.catalog.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class PlanBundleItem {

    private final UUID id;
    private final UUID planBundleId;
    private final UUID planId;
    private boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PlanBundleItem(
            UUID id,
            UUID planBundleId,
            UUID planId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.planBundleId = requireId(planBundleId, "Plan bundle");
        this.planId = requireId(planId, "Plan bundle item plan");
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlanBundleItem create(UUID planBundleId, UUID planId) {
        return new PlanBundleItem(null, planBundleId, planId, true, null, null);
    }

    public static PlanBundleItem rehydrate(
            UUID id,
            UUID planBundleId,
            UUID planId,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PlanBundleItem(id, planBundleId, planId, active, createdAt, updatedAt);
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
