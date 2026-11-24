package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.util.UUID;

public record BillingControlSummaryResponse(UUID contractId, UUID workId, String reference, String name,
                                            ContractStatus status,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "100.00") BigDecimal contractNetAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "40.00") BigDecimal activeAllocatedAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "60.00") BigDecimal availableAmount,
        com.renovar.canteiro.io.shared.domain.CurrencyCode currency,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True only with effective RECEIVABLES.READ in addition to BILLING.READ. False means receivedAmount is unavailable, not zero.")
        boolean receivedAmountAvailable,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(types = {"string", "null"}, format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "40.00",
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Official sum of signed CONFIRMED receipts and reversals of this contract in the authenticated company, including historical/renegotiated titles without double counting. Retentions, credits, NFs and 202 proposals are not cash. Null without RECEIVABLES.READ; authorized empty history returns 0.00. Never sum titles or versions in the frontend.")
        BigDecimal receivedAmount) {
}
