package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface ReceivableSettlementRepository {

    org.springframework.data.domain.Page<ReceivableSettlementHistory> findHistory(
            UUID companyId, UUID receivableId, UUID contractId, ReceivableSettlementFilter filter,
            org.springframework.data.domain.Pageable pageable);

    ReceivableSettlement save(ReceivableSettlement receivableSettlement);

    List<ReceivableSettlement> findByReceivableIdAndCompanyId(UUID receivableId, UUID companyId);

    BigDecimal confirmedAmountByReceivableIdAndCompanyId(UUID receivableId, UUID companyId);

    java.util.Map<UUID, BigDecimal> confirmedAmountsByContractIdsAndCompanyId(List<UUID> contractIds, UUID companyId);

    Optional<ReceivableSettlement> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsReversalOfSettlementIdAndCompanyId(UUID settlementId, UUID companyId);
}
