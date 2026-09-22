package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface BillingInstallmentJpaRepository extends JpaRepository<BillingInstallmentJpaEntity, UUID> {

    Optional<BillingInstallmentJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
