package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPayableRepository implements PayableRepository {

    private final PayableJpaRepository payableJpaRepository;

    @Override
    public Payable save(Payable payable) {
        return toDomain(payableJpaRepository.save(new PayableJpaEntity(
                payable.getCompanyId(), payable.getFinancialScope(), payable.getWorkId(), payable.getContractId(),
                payable.getTaxObligationId(), payable.getReference(), payable.getDescription(), payable.getDueOn(),
                payable.getOriginalAmount(), payable.getStatus()
        )));
    }

    @Override
    public Optional<Payable> findByTaxObligationIdAndCompanyId(UUID taxObligationId, UUID companyId) {
        return payableJpaRepository.findByTaxObligationIdAndCompanyId(taxObligationId, companyId).map(this::toDomain);
    }

    @Override
    public Payable updateStatus(Payable payable) {
        PayableJpaEntity entity = payableJpaRepository.findById(payable.getId()).orElseThrow();
        if (!entity.getCompanyId().equals(payable.getCompanyId())) throw new IllegalArgumentException("Payable tenant mismatch");
        entity.setStatus(payable.getStatus());
        return toDomain(payableJpaRepository.save(entity));
    }

    private Payable toDomain(PayableJpaEntity entity) {
        return Payable.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getFinancialScope(), entity.getWorkId(), entity.getContractId(),
                entity.getTaxObligationId(), entity.getReference(), entity.getDescription(), entity.getDueOn(),
                entity.getOriginalAmount(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
