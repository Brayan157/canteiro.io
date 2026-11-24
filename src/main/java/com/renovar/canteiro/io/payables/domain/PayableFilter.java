package com.renovar.canteiro.io.payables.domain;

import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.time.LocalDate;
import java.util.UUID;

public record PayableFilter(PayableFinancialScope financialScope, UUID workId, UUID contractId, UUID supplierId,
        UUID financialCategoryId, UUID financialGroupId, CurrencyCode currency, PayableStatus status, String reference,
        LocalDate dueFrom, LocalDate dueTo, PayableView view) {
    public PayableFilter {
        reference=PayableCatalogValues.text(reference,100,false);
        if(dueFrom!=null&&dueTo!=null&&dueFrom.isAfter(dueTo))throw new IllegalArgumentException("dueFrom must not exceed dueTo");
        if(financialScope==PayableFinancialScope.COMPANY&&(workId!=null||contractId!=null))throw new IllegalArgumentException("COMPANY filter forbids workId and contractId");
    }
}
