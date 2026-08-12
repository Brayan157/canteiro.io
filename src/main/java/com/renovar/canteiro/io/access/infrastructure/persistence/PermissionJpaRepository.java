package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

interface PermissionJpaRepository extends JpaRepository<PermissionJpaEntity, UUID> {

    Optional<PermissionJpaEntity> findByModuleAndAction(AccessModule module, AccessAction action);

    Page<PermissionJpaEntity> findAll(Pageable pageable);
}
