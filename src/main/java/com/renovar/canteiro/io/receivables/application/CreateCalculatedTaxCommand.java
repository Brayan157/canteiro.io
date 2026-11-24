package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.TaxCalculation;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCalculatedTaxCommand(UUID operationId, UUID invoiceId, UUID receivableId, String name,
        String description, LocalDate dueOn, TaxTreatment treatment, String justification,
        TaxCalculation calculation, BigDecimal confirmedAmount) {
    public CreateTaxObligationCommand legacy() {
        return new CreateTaxObligationCommand(invoiceId, receivableId, name, description, dueOn, calculation.amount(), treatment, justification);
    }
}
