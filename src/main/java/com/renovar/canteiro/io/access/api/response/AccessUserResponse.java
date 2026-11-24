package com.renovar.canteiro.io.access.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renovar.canteiro.io.identity.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(name = "AccessUserResponse", description = "Company system user and its assigned access roles")
public record AccessUserResponse(
        UUID id,
        String email,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Full name of the employee explicitly linked to this user in the same company. Omitted when unavailable.",
                example = "Ana Souza")
        String displayName,
        UserStatus status,
        Set<UUID> roleIds,
        @Schema(description = "True only for the immutable initial owner of this company")
        boolean companyOwner,
        Instant createdAt,
        Instant updatedAt
) {
}
