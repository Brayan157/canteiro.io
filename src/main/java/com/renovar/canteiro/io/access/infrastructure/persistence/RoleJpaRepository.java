package com.renovar.canteiro.io.access.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    Optional<RoleJpaEntity> findByIdAndCompanyId(UUID roleId, UUID companyId);

    Page<RoleJpaEntity> findByCompanyId(UUID companyId, Pageable pageable);
}
