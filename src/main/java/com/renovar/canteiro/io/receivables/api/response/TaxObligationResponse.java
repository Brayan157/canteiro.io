package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.TaxObligationStatus;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

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
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "10.00") BigDecimal amount,
        TaxTreatment treatment,
        TaxObligationStatus status,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency
) {
}
