package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface NetBillingRepository {
    void lockOperation(UUID companyId, UUID operationId);
    Optional<Operation> findOperation(UUID companyId, UUID operationId);
    void remember(UUID companyId, UUID operationId, UUID contractId, String fingerprint, UUID installmentId, UUID requestId);
    void complete(UUID companyId, UUID operationId, UUID requestId, UUID installmentId);
    Set<UUID> legacyConvertedServiceIds(UUID companyId, UUID contractId);

    record Operation(UUID contractId, String fingerprint, UUID installmentId, UUID changeRequestId) {
    }
}
