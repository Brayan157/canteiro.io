package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.payables.domain.PayableFinancialScope;
import com.renovar.canteiro.io.payables.domain.PayableCatalogValues;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PayableCreateCommand(UUID operationId, PayableFinancialScope financialScope, UUID workId, UUID contractId,
        UUID supplierId, UUID financialCategoryId, CurrencyCode currency, List<Title> titles, String justification) {
    public PayableCreateCommand {
        if(operationId==null||titles==null||titles.isEmpty()||titles.size()>100)throw new IllegalArgumentException("Operation ID and 1 to 100 titles are required");
        titles=List.copyOf(titles);
        justification=PayableCatalogValues.text(justification,1000,false);
    }
    public record Title(String reference,String description,LocalDate issuedOn,LocalDate dueOn,BigDecimal originalAmount) {
        public Title {
            reference=PayableCatalogValues.text(reference,100,true);
            description=PayableCatalogValues.text(description,1000,false);
        }
    }
}
