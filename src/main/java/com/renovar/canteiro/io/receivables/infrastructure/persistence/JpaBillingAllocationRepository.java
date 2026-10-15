package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaBillingAllocationRepository implements BillingAllocationRepository {

    private final BillingAllocationJpaRepository billingAllocationJpaRepository;

    @Override
    public BillingAllocation save(BillingAllocation billingAllocation) {
        return toDomain(billingAllocationJpaRepository.save(new BillingAllocationJpaEntity(
                billingAllocation.getCompanyId(), billingAllocation.getBillingInstallmentId(), billingAllocation.getContractId(),
                billingAllocation.getContractServiceId(), billingAllocation.getMeasurementContractCompositionId(), billingAllocation.getType(), billingAllocation.getAllocatedAmount(),
                billingAllocation.getStatus()
        )));
    }

    @Override
    public Optional<BillingAllocation> findByIdAndCompanyId(UUID id, UUID companyId) {
        return billingAllocationJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public List<BillingAllocation> findByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId) {
        return billingAllocationJpaRepository.findByBillingInstallmentIdAndCompanyId(billingInstallmentId, companyId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BillingAllocation> findByContractIdAndCompanyId(UUID contractId, UUID companyId) {
        return billingAllocationJpaRepository.findByContractIdAndCompanyId(contractId, companyId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<BillingAllocation> findByMeasurementContractCompositionIdAndCompanyId(UUID compositionId, UUID companyId) {
        return billingAllocationJpaRepository.findByMeasurementContractCompositionIdAndCompanyId(compositionId, companyId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public BillingAllocation updateStatus(BillingAllocation allocation) {
        BillingAllocationJpaEntity entity = billingAllocationJpaRepository
                .findByIdAndCompanyId(allocation.getId(), allocation.getCompanyId()).orElseThrow();
        entity.setStatus(allocation.getStatus());
        return toDomain(billingAllocationJpaRepository.save(entity));
    }

    private BillingAllocation toDomain(BillingAllocationJpaEntity entity) {
        return BillingAllocation.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getBillingInstallmentId(), entity.getContractId(),
                entity.getContractServiceId(), entity.getMeasurementContractCompositionId(), entity.getType(), entity.getAllocatedAmount(), entity.getStatus(), entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
