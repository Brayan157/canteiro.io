package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvoiceItemRepository {

    InvoiceItem save(InvoiceItem invoiceItem);

    List<InvoiceItem> findByInvoiceIdAndCompanyId(UUID invoiceId, UUID companyId);

    BigDecimal registeredAmountByBillingAllocationIdAndCompanyId(UUID billingAllocationId, UUID companyId);

    BigDecimal registeredAmountByContractIdAndCompanyId(UUID contractId, UUID companyId);

    InvoiceItem updateStatus(InvoiceItem invoiceItem);
}
