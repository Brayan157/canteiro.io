package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.TaxTreatment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTaxObligationCommand(
        UUID invoiceId,
        UUID receivableId,
        String name,
        String description,
        LocalDate dueOn,
        BigDecimal amount,
        TaxTreatment treatment,
        String justification
) {
}
