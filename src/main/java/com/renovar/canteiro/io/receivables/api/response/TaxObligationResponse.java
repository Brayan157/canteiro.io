package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxObligationResponse(
        UUID id,
        UUID invoiceId,
        UUID contractId,
        UUID receivableId,
        String name,
        String description,
        LocalDate dueOn,
        BigDecimal amount,
        TaxTreatment treatment,
        TaxObligationStatus status
) {
}
