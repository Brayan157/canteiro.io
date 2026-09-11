package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTaxObligationRequest(
        UUID receivableId,
        @NotBlank String name,
        String description,
        LocalDate dueOn,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal amount,
        @NotNull TaxTreatment treatment,
        String justification
) {
}
