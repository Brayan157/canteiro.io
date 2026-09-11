package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ContractBillingControlResponse(UUID contractId, UUID workId, String reference, String name,
                                             ContractStatus status, BigDecimal contractNetAmount,
                                             BigDecimal activeAllocatedAmount, BigDecimal availableAmount,
                                             List<BillingServiceControlResponse> services,
                                             List<BillingAllocationControlResponse> allocations) {
}
