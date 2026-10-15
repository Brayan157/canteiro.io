package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingAllocationRequest(
        UUID contractServiceId,
        UUID measurementContractCompositionId,
        @NotNull BillingAllocationType type,
        @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "decimal", example = "100.00", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$")
        @NotNull BigDecimal allocatedAmount
) {
}
