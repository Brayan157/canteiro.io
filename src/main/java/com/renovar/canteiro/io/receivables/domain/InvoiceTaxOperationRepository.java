package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceTaxOperationRepository {
    void lock(UUID company, UUID operation);
    Optional<Operation> find(UUID company, UUID operation);
    Optional<Operation> findByTax(UUID company, UUID tax);
    void remember(UUID company, UUID operation, UUID invoice, UUID contract, String fingerprint, TaxCalculation calculation, UUID tax, UUID request);
    void complete(UUID company, UUID operation, UUID request, UUID tax);
    record Operation(UUID operationId, UUID invoiceId, String fingerprint, TaxCalculation calculation, UUID taxId, UUID requestId) { }
}
