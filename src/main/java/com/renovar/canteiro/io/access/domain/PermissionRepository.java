package com.renovar.canteiro.io.access.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository {

    Permission save(Permission permission);

    Optional<Permission> findByModuleAndAction(AccessModule module, AccessAction action);

    Optional<Permission> findById(UUID permissionId);

    Page<Permission> findAll(Pageable pageable);
}
