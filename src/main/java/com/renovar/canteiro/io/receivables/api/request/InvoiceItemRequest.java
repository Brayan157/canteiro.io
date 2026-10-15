package com.renovar.canteiro.io.receivables.api.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemRequest(
        @NotNull UUID billingAllocationId,
        String description,
        @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "decimal", example = "100.00", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$")
        @NotNull BigDecimal allocatedAmount
) {
}
