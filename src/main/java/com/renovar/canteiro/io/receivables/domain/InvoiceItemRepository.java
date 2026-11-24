package com.renovar.canteiro.io.receivables.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvoiceItemRepository {

    InvoiceItem save(InvoiceItem invoiceItem);

    List<InvoiceItem> findByInvoiceIdAndCompanyId(UUID invoiceId, UUID companyId);

    BigDecimal registeredAmountByBillingAllocationIdAndCompanyId(UUID billingAllocationId, UUID companyId);

    default java.util.Map<UUID,BigDecimal> registeredAmountsByBillingAllocationIdsAndCompanyId(java.util.Collection<UUID> allocationIds,UUID companyId){
        var amounts=new java.util.HashMap<UUID,BigDecimal>();
        for(UUID id:allocationIds)amounts.put(id,registeredAmountByBillingAllocationIdAndCompanyId(id,companyId));
        return amounts;
    }

    BigDecimal registeredAmountByContractIdAndCompanyId(UUID contractId, UUID companyId);

    InvoiceItem updateStatus(InvoiceItem invoiceItem);
}
