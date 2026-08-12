package com.renovar.canteiro.io.platform.catalog.api.response;

import java.time.Instant;
import java.util.UUID;

public record CatalogAssignmentResponse(
        UUID id,
        UUID parentId,
        UUID childId,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
