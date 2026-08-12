package com.renovar.canteiro.io.platform.subscription.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class SubscriptionItem {

    private final UUID id;
    private final UUID subscriptionId;
    private final UUID planId;
    private final String planCode;
    private final String planName;
    private final Instant createdAt;
    private final Instant updatedAt;

    private SubscriptionItem(
            UUID id,
            UUID subscriptionId,
            UUID planId,
            String planCode,
            String planName,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.subscriptionId = requireId(subscriptionId, "A subscription item subscription is required");
        this.planId = requireId(planId, "A subscription item plan is required");
        this.planCode = requireText(planCode, "A subscription item plan code is required");
        this.planName = requireText(planName, "A subscription item plan name is required");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SubscriptionItem create(UUID subscriptionId, UUID planId, String planCode, String planName) {
        return new SubscriptionItem(null, subscriptionId, planId, planCode, planName, null, null);
    }

    public static SubscriptionItem rehydrate(
            UUID id,
            UUID subscriptionId,
            UUID planId,
            String planCode,
            String planName,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new SubscriptionItem(id, subscriptionId, planId, planCode, planName, createdAt, updatedAt);
    }

    private static UUID requireId(UUID value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
