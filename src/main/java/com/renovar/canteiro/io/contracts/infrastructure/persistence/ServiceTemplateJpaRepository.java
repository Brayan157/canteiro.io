package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

interface ServiceTemplateJpaRepository extends JpaRepository<ServiceTemplateJpaEntity, UUID> {

    Optional<ServiceTemplateJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    List<ServiceTemplateJpaEntity> findByCompanyIdOrderByNameAsc(UUID companyId);

    List<ServiceTemplateJpaEntity> findByCompanyIdAndActiveTrueOrderByNameAsc(UUID companyId);
}
