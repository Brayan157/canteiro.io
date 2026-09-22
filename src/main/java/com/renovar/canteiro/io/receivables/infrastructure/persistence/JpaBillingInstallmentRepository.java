package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaBillingInstallmentRepository implements BillingInstallmentRepository {

    private final BillingInstallmentJpaRepository billingInstallmentJpaRepository;

    @Override
    public BillingInstallment save(BillingInstallment billingInstallment) {
        return toDomain(billingInstallmentJpaRepository.save(new BillingInstallmentJpaEntity(
                billingInstallment.getCompanyId(), billingInstallment.getContractId(), billingInstallment.getReference(),
                billingInstallment.getDescription(), billingInstallment.getIssuedOn(), billingInstallment.getDueOn(),
                billingInstallment.getStatus()
        )));
    }

    @Override
    public Optional<BillingInstallment> findByIdAndCompanyId(UUID id, UUID companyId) {
        return billingInstallmentJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public BillingInstallment updateStatus(BillingInstallment installment) {
        BillingInstallmentJpaEntity entity = billingInstallmentJpaRepository
                .findByIdAndCompanyId(installment.getId(), installment.getCompanyId()).orElseThrow();
        entity.setStatus(installment.getStatus());
        return toDomain(billingInstallmentJpaRepository.save(entity));
    }

    private BillingInstallment toDomain(BillingInstallmentJpaEntity entity) {
        return BillingInstallment.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getContractId(), entity.getReference(), entity.getDescription(),
                entity.getIssuedOn(), entity.getDueOn(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
