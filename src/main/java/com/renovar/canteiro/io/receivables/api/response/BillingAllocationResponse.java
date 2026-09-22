package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingAllocationResponse(
        UUID id,
        UUID contractServiceId,
        BillingAllocationType type,
        BigDecimal allocatedAmount,
        BillingAllocationStatus status
) {
}
