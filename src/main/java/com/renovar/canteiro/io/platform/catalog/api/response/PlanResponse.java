package com.renovar.canteiro.io.platform.catalog.api.response;

import java.time.Instant;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
