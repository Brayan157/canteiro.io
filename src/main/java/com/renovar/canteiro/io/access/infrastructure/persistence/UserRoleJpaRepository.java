package com.renovar.canteiro.io.access.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserRoleJpaRepository extends JpaRepository<UserRoleJpaEntity, UUID> {

    List<UserRoleJpaEntity> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    Optional<UserRoleJpaEntity> findByUserIdAndRoleIdAndCompanyId(UUID userId, UUID roleId, UUID companyId);
}
