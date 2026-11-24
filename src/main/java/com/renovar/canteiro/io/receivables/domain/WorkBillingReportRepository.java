package com.renovar.canteiro.io.receivables.domain;

import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

public interface WorkBillingReportRepository {
    void validateInvoiceOrigins(UUID companyId, UUID workId);

    /** Visits one official position per contract in ascending contract ID order. */
    void scan(UUID companyId, UUID workId, Consumer<ContractPosition> consumer);

    record ContractPosition(UUID id, String identifier, String reference, String name, String status, CurrencyCode currency,
                            ContractNetAmount commercial, BigDecimal invoicedAmount) {
        public BigDecimal documentaryBalance() {
            return commercial.netAmount().subtract(invoicedAmount);
        }
    }
}
