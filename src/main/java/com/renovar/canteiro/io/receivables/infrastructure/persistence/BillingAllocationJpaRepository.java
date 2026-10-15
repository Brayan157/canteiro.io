package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BillingAllocationJpaRepository extends JpaRepository<BillingAllocationJpaEntity, UUID> {

    Optional<BillingAllocationJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    List<BillingAllocationJpaEntity> findByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId);

    List<BillingAllocationJpaEntity> findByContractIdAndCompanyId(UUID contractId, UUID companyId);

    List<BillingAllocationJpaEntity> findByMeasurementContractCompositionIdAndCompanyId(UUID measurementContractCompositionId, UUID companyId);
}
