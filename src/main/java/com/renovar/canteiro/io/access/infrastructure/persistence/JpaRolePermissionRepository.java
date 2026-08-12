package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaRolePermissionRepository implements RolePermissionRepository {

    private final RolePermissionJpaRepository rolePermissionJpaRepository;
    private final RolePermissionPersistenceMapper rolePermissionPersistenceMapper;

    @Override
    public RolePermission save(RolePermission rolePermission) {
        if (rolePermission.getId() == null) {
            return rolePermissionPersistenceMapper.toDomain(
                    rolePermissionJpaRepository.save(rolePermissionPersistenceMapper.toJpaEntity(rolePermission))
            );
        }
        RolePermissionJpaEntity entity = rolePermissionJpaRepository.findById(rolePermission.getId())
                .orElseThrow(() -> new IllegalStateException("Role permission must exist before it can be updated"));
        rolePermissionPersistenceMapper.updateJpaEntity(entity, rolePermission);
        return rolePermissionPersistenceMapper.toDomain(rolePermissionJpaRepository.save(entity));
    }

    @Override
    public List<RolePermission> findByRoleId(UUID roleId) {
        return rolePermissionJpaRepository.findByRoleId(roleId).stream()
                .map(rolePermissionPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<RolePermission> findByRoleIdAndPermissionId(UUID roleId, UUID permissionId) {
        return rolePermissionJpaRepository.findByRoleIdAndPermissionId(roleId, permissionId)
                .map(rolePermissionPersistenceMapper::toDomain);
    }
}
