package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

interface ReceivableSettlementJpaRepository extends JpaRepository<ReceivableSettlementJpaEntity, UUID> {

    List<ReceivableSettlementJpaEntity> findByReceivableIdAndCompanyId(UUID receivableId, UUID companyId);

    Optional<ReceivableSettlementJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByReversalOfSettlementIdAndCompanyId(UUID settlementId, UUID companyId);

    @Query("""
            select coalesce(sum(settlement.amount), 0)
            from ReceivableSettlementJpaEntity settlement
            where settlement.receivableId = :receivableId
              and settlement.companyId = :companyId
              and settlement.status = :status
            """)
    BigDecimal sumAmountByReceivableIdAndCompanyIdAndStatus(
            @Param("receivableId") UUID receivableId,
            @Param("companyId") UUID companyId,
            @Param("status") ReceivableSettlementStatus status
    );
}
