package com.renovar.canteiro.io.receivables.api.request;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingAllocationRequest(
        UUID contractServiceId,
        @NotNull BillingAllocationType type,
        @NotNull BigDecimal allocatedAmount
) {
}
