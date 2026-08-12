package com.renovar.canteiro.io.access.api.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        Set<UUID> permissionIds,
        Instant createdAt,
        Instant updatedAt
) {
}
