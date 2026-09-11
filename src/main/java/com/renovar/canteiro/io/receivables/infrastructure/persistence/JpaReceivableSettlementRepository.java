package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaReceivableSettlementRepository implements ReceivableSettlementRepository {

    private final ReceivableSettlementJpaRepository receivableSettlementJpaRepository;

    @Override
    public ReceivableSettlement save(ReceivableSettlement settlement) {
        return toDomain(receivableSettlementJpaRepository.save(new ReceivableSettlementJpaEntity(
                settlement.getCompanyId(), settlement.getReceivableId(), settlement.getContractId(), settlement.getReceivedOn(),
                settlement.getAmount(), settlement.getEntryType(), settlement.getReversalOfSettlementId(),
                settlement.getPaymentMethod(), settlement.getNote(), settlement.getStatus()
        )));
    }

    @Override
    public List<ReceivableSettlement> findByReceivableIdAndCompanyId(UUID receivableId, UUID companyId) {
        return receivableSettlementJpaRepository.findByReceivableIdAndCompanyId(receivableId, companyId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public BigDecimal confirmedAmountByReceivableIdAndCompanyId(UUID receivableId, UUID companyId) {
        return receivableSettlementJpaRepository.sumAmountByReceivableIdAndCompanyIdAndStatus(
                receivableId, companyId, ReceivableSettlementStatus.CONFIRMED
        );
    }

    @Override
    public Optional<ReceivableSettlement> findByIdAndCompanyId(UUID id, UUID companyId) {
        return receivableSettlementJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public boolean existsReversalOfSettlementIdAndCompanyId(UUID settlementId, UUID companyId) {
        return receivableSettlementJpaRepository.existsByReversalOfSettlementIdAndCompanyId(settlementId, companyId);
    }

    private ReceivableSettlement toDomain(ReceivableSettlementJpaEntity entity) {
        return ReceivableSettlement.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getReceivableId(), entity.getContractId(), entity.getReceivedOn(),
                entity.getAmount(), entity.getEntryType(), entity.getReversalOfSettlementId(), entity.getPaymentMethod(),
                entity.getNote(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
