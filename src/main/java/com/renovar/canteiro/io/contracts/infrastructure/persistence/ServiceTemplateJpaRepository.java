package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ServiceTemplateJpaRepository extends JpaRepository<ServiceTemplateJpaEntity, UUID> {

    Optional<ServiceTemplateJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
