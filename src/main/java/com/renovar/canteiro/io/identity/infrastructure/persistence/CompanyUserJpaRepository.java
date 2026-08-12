package com.renovar.canteiro.io.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CompanyUserJpaRepository extends JpaRepository<CompanyUserJpaEntity, UUID> {

    Optional<CompanyUserJpaEntity> findByUserId(UUID userId);

    Optional<CompanyUserJpaEntity> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    Page<CompanyUserJpaEntity> findByCompanyId(UUID companyId, Pageable pageable);
}
