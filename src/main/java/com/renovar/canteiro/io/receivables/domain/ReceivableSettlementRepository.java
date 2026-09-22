package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface ReceivableSettlementRepository {

    ReceivableSettlement save(ReceivableSettlement receivableSettlement);

    List<ReceivableSettlement> findByReceivableIdAndCompanyId(UUID receivableId, UUID companyId);

    BigDecimal confirmedAmountByReceivableIdAndCompanyId(UUID receivableId, UUID companyId);

    Optional<ReceivableSettlement> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsReversalOfSettlementIdAndCompanyId(UUID settlementId, UUID companyId);
}
