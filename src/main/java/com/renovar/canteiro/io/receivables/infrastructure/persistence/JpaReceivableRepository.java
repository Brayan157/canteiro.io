package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaReceivableRepository implements ReceivableRepository {

    private final ReceivableJpaRepository receivableJpaRepository;

    @Override
    public Receivable save(Receivable receivable) {
        return toDomain(receivableJpaRepository.save(new ReceivableJpaEntity(
                receivable.getCompanyId(), receivable.getContractId(), receivable.getBillingInstallmentId(),
                receivable.getFinancialGroupId(), receivable.getReference(), receivable.getDescription(),
                receivable.getIssuedOn(), receivable.getDueOn(), receivable.getOriginalAmount(), receivable.getStatus()
        )));
    }

    @Override
    public Optional<Receivable> findByIdAndCompanyId(UUID id, UUID companyId) {
        return receivableJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<Receivable> findByIdAndCompanyIdWithSettlementLock(UUID id, UUID companyId) {
        return receivableJpaRepository.findByIdAndCompanyIdWithSettlementLock(id, companyId).map(this::toDomain);
    }

    @Override
    public Receivable updateStatus(Receivable receivable) {
        ReceivableJpaEntity entity = receivableJpaRepository.findByIdAndCompanyId(receivable.getId(), receivable.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Receivable was not found in its company"));
        entity.setStatus(receivable.getStatus());
        return toDomain(receivableJpaRepository.save(entity));
    }

    @Override
    public Optional<Receivable> findByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId) {
        return receivableJpaRepository.findByBillingInstallmentIdAndCompanyId(billingInstallmentId, companyId)
                .map(this::toDomain);
    }

    private Receivable toDomain(ReceivableJpaEntity entity) {
        return Receivable.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getContractId(), entity.getBillingInstallmentId(),
                entity.getFinancialGroupId(), entity.getReference(), entity.getDescription(), entity.getIssuedOn(),
                entity.getDueOn(), entity.getOriginalAmount(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
