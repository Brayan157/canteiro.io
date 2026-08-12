package com.renovar.canteiro.io.access.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RolePermissionRepository {

    RolePermission save(RolePermission rolePermission);

    List<RolePermission> findByRoleId(UUID roleId);

    Optional<RolePermission> findByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
}
