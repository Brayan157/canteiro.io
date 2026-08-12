package com.renovar.canteiro.io.access.application;

import java.util.Set;
import java.util.UUID;

public record ReplaceRolePermissionsCommand(Set<UUID> permissionIds) {
}
