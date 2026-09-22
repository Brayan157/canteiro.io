package com.renovar.canteiro.io.receivables.api.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemRequest(
        @NotNull UUID billingAllocationId,
        String description,
        @NotNull BigDecimal allocatedAmount
) {
}
