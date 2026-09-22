package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record ContractBillingControlResponse(UUID contractId, UUID workId, String reference, String name,
                                             ContractStatus status,
                                             @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String contractNetAmount,
                                             @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String activeAllocatedAmount,
                                             @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String availableAmount,
                                             List<BillingServiceControlResponse> services,
                                             List<BillingAllocationControlResponse> allocations) {
}
