package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaPermissionRepository implements PermissionRepository {

    private final PermissionJpaRepository permissionJpaRepository;
    private final PermissionPersistenceMapper permissionPersistenceMapper;

    @Override
    public Permission save(Permission permission) {
        if (permission.getId() != null) {
            throw new IllegalStateException("Permission updates are not implemented yet");
        }
        return permissionPersistenceMapper.toDomain(
                permissionJpaRepository.save(permissionPersistenceMapper.toJpaEntity(permission))
        );
    }

    @Override
    public Optional<Permission> findByModuleAndAction(AccessModule module, AccessAction action) {
        return permissionJpaRepository.findByModuleAndAction(module, action).map(permissionPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Permission> findById(java.util.UUID permissionId) {
        return permissionJpaRepository.findById(permissionId).map(permissionPersistenceMapper::toDomain);
    }

    @Override
    public Page<Permission> findAll(Pageable pageable) {
        return permissionJpaRepository.findAll(pageable).map(permissionPersistenceMapper::toDomain);
    }
}
