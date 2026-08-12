package com.renovar.canteiro.io.platform.catalog.api.response;

import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureType;

import java.time.Instant;
import java.util.UUID;

public record PlanFeatureResponse(
        UUID id,
        String code,
        PlanFeatureType type,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
