package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingControlSummaryResponse(UUID contractId, UUID workId, String reference, String name,
                                            ContractStatus status, BigDecimal contractNetAmount,
                                            BigDecimal activeAllocatedAmount, BigDecimal availableAmount) {
}
