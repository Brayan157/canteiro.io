package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingAllocationCommand(
        UUID contractServiceId,
        UUID measurementContractCompositionId,
        BillingAllocationType type,
        BigDecimal allocatedAmount
) {
    /** Compatibility constructor for allocations with no measurement origin. */
    public BillingAllocationCommand(UUID contractServiceId, BillingAllocationType type, BigDecimal allocatedAmount) {
        this(contractServiceId, null, type, allocatedAmount);
    }
}
