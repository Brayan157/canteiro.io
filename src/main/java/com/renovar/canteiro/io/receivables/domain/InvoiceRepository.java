package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    Invoice save(Invoice invoice);

    Optional<Invoice> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Invoice> findByIdAndCompanyIdWithLock(UUID id, UUID companyId);

    Invoice updateStatus(Invoice invoice);

    boolean existsRegisteredByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId);
}
