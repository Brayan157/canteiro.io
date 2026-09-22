package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository {

    Receivable save(Receivable receivable);

    Optional<Receivable> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Receivable> findByIdAndCompanyIdWithSettlementLock(UUID id, UUID companyId);

    Receivable updateStatus(Receivable receivable);

    Optional<Receivable> findByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId);
}
