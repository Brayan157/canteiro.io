package com.renovar.canteiro.io.receivables.domain;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
public interface ReceivableOriginRepository {
    void lockInstallment(UUID companyId,UUID installmentId);
    ReceivableCredit saveCredit(ReceivableCredit credit);
    Optional<ReceivableCredit> findCredit(UUID companyId,UUID creditId);
    boolean hasCreditReversal(UUID companyId,UUID creditId);
    BigDecimal effectiveCredits(UUID companyId,UUID receivableId);
    void attributeSettlement(UUID companyId,UUID settlementId,UUID allocationId,BigDecimal signedAmount);
    Map<UUID,BigDecimal> settlementAttribution(UUID companyId,UUID settlementId);
    Map<UUID,BigDecimal> receivedByAllocation(UUID companyId,UUID installmentId);
}

