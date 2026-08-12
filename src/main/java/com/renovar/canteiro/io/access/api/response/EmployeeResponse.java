package com.renovar.canteiro.io.access.api.response;

import com.renovar.canteiro.io.identity.domain.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record EmployeeResponse(
        UUID id,
        String email,
        UserStatus status,
        Set<UUID> roleIds,
        Instant createdAt,
        Instant updatedAt
) {
}
