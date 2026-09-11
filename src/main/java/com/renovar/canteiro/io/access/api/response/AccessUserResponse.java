package com.renovar.canteiro.io.access.api.response;

import com.renovar.canteiro.io.identity.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(name = "AccessUserResponse", description = "Company system user and its assigned access roles")
public record AccessUserResponse(
        UUID id,
        String email,
        UserStatus status,
        Set<UUID> roleIds,
        @Schema(description = "True only for the immutable initial owner of this company")
        boolean companyOwner,
        Instant createdAt,
        Instant updatedAt
) {
}
