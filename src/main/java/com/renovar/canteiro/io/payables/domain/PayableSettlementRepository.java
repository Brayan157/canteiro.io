package com.renovar.canteiro.io.payables.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayableSettlementRepository {
    void lockOperation(UUID companyId, UUID operationId);
    Optional<Operation> findOperation(UUID companyId, UUID operationId);
    void saveOperation(UUID companyId, UUID operationId, Operation operation);
    Optional<PayableSettlement> find(UUID companyId, UUID payableId, UUID settlementId);
    Optional<PayableSettlement> findApplied(UUID companyId, UUID payableId, UUID operationId);
    boolean hasReversal(UUID companyId, UUID payableId, UUID settlementId);
    Map<UUID, BigDecimal> netPaid(UUID companyId, List<UUID> payableIds);
    Page<PayableSettlement> history(UUID companyId, UUID payableId, Pageable page);
    void save(PayableSettlement settlement);

    record Operation(UUID payableId, String fingerprint, UUID changeRequestId) { }
}
