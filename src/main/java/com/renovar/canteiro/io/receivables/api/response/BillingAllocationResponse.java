package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingAllocationResponse(
        UUID id,
        UUID contractServiceId,
        UUID measurementContractCompositionId,
        BillingAllocationType type,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "100.00") BigDecimal allocatedAmount,
        BillingAllocationStatus status,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(types = {"string", "null"}, format = "decimal", example = "100.00", description = "Immutable gross basis consumed by net billing; null for legacy allocations.") BigDecimal netBillingGrossAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(types = {"string", "null"}, format = "decimal", example = "10.00", description = "Immutable contract discount applied by net billing; null for legacy allocations.") BigDecimal netBillingDiscountAmount
) {
    public BillingAllocationResponse(UUID id, UUID service, UUID origin, BillingAllocationType type, BigDecimal amount, BillingAllocationStatus status) {
        this(id, service, origin, type, amount, status, null, null);
    }
    public static BillingAllocationResponse from(BillingAllocation allocation) {
        return new BillingAllocationResponse(allocation.getId(), allocation.getContractServiceId(), allocation.getMeasurementContractCompositionId(),
                allocation.getType(), allocation.getAllocatedAmount(), allocation.getStatus(), allocation.getNetBillingGrossAmount(), allocation.getNetBillingDiscountAmount());
    }
}
