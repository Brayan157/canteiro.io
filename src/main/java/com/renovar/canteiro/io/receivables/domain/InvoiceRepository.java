package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {

    org.springframework.data.domain.Page<InvoiceContractSummary> findContractsWithInvoices(UUID companyId, String search,
            org.springframework.data.domain.Pageable pageable);

    Optional<InvoiceContractSummary> findContractWithInvoicesByIdAndCompanyId(UUID contractId, UUID companyId);

    org.springframework.data.domain.Page<InvoiceSummary> findByCompanyId(UUID companyId, InvoiceFilter filter,
                                                                       org.springframework.data.domain.Pageable pageable);

    Invoice save(Invoice invoice);

    Optional<Invoice> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Invoice> findByIdAndCompanyIdWithLock(UUID id, UUID companyId);

    Invoice updateStatus(Invoice invoice);

    boolean existsRegisteredByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId);
}
