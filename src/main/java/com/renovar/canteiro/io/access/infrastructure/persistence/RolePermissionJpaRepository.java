package com.renovar.canteiro.io.access.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RolePermissionJpaRepository extends JpaRepository<RolePermissionJpaEntity, UUID> {

    List<RolePermissionJpaEntity> findByRoleId(UUID roleId);

    Optional<RolePermissionJpaEntity> findByRoleIdAndPermissionId(UUID roleId, UUID permissionId);
}
