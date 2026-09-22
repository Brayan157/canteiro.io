package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingAllocationCommand(
        UUID contractServiceId,
        BillingAllocationType type,
        BigDecimal allocatedAmount
) {
}
