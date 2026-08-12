package com.renovar.canteiro.io.access.api.request;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record ReplaceRolePermissionsRequest(
        @NotNull Set<UUID> permissionIds
) {
}
