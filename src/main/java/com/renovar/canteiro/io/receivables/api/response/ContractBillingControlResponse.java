package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record ContractBillingControlResponse(UUID contractId, UUID workId, String reference, String name,
                                             ContractStatus status,
                                             @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String serviceSubtotal,
                                             @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String contractDiscountAmount,
                                             @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String contractNetAmount,
                                             @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String activeAllocatedAmount,
                                             @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String availableAmount,
                                             List<BillingServiceControlResponse> services,
                                             List<BillingAllocationControlResponse> allocations,
                                             com.renovar.canteiro.io.shared.domain.CurrencyCode currency,
                                             @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True only with effective RECEIVABLES.READ in addition to BILLING.READ. False means receivedAmount is unavailable, not zero.")
                                             boolean receivedAmountAvailable,
                                             @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                                             @Schema(types = {"string", "null"}, format = "decimal", example = "40.00", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$",
                                                     requiredMode = Schema.RequiredMode.REQUIRED,
                                                     description = "Official sum of signed CONFIRMED receipts and reversals of this contract in the authenticated company. Includes historical/renegotiated titles, without double counting. Not retention, credit, invoice value or a 202 proposal. Null without RECEIVABLES.READ; authorized empty history returns 0.00. This aggregate does not establish receipt eligibility or invoice coverage.")
                                             String receivedAmount) {
}
