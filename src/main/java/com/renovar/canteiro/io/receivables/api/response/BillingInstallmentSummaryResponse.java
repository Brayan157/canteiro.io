package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import com.renovar.canteiro.io.receivables.application.ReadBillingInstallmentsUseCase;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Official persisted installment, including cancellation history. netAmount is the signed sum of its allocations, not its remaining receivable balance.",
        requiredProperties = {"id", "contractId", "dueOn", "status", "currency", "netAmount", "createdAt", "updatedAt"})
public record BillingInstallmentSummaryResponse(UUID id, UUID contractId,
        @Schema(types = {"string", "null"}) String reference,
        @Schema(types = {"string", "null"}, format = "date") LocalDate issuedOn, LocalDate dueOn,
        @Schema(description = "Persisted business status, not the current receipt status. Status filters and sorting use this field; use receivableFinancialStatus for the financial badge when available.") BillingInstallmentStatus status, CurrencyCode currency,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "140.00") BigDecimal netAmount,
        Instant createdAt, Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True only when RECEIVABLES.READ is granted in addition to BILLING.READ and the installment original receivable has an official financial position. False means unavailable, never OPEN or zero.") boolean receivableFinancialStatusAvailable,
                                               @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                                               @Schema(types = {"string", "null"}, allowableValues = {"OPEN", "PARTIALLY_RECEIVED", "RECEIVED", "SETTLED_WITH_ADJUSTMENTS", "CANCELLED", "RENEGOTIATED"}, requiredMode = Schema.RequiredMode.REQUIRED,
                                                       description = "Official current status of the ORIGINAL receivable linked to this installment, using confirmed cash net of reversals, withholding and credits. Settled by adjustments is not cash RECEIVED. Preserves cancelled/renegotiated originals, does not aggregate children or imply settlement of an individual service/allocation. Null without permission or without an original title; no NF/date/client inference, and 202 proposals have no effect.")
                                               com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition.Status receivableFinancialStatus,
                                               @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                                               @Schema(types = {"boolean", "null"}, requiredMode = Schema.RequiredMode.REQUIRED,
                                                       description = "Presentation hint from the same authorized original-title financial position: true only for SETTLED_WITH_ADJUSTMENTS with zero outstanding, positive confirmed cash net of reversals, positive withholding and zero credits. UI may show Received with tax withholding. Does not classify withholding as cash or change financial status/amounts. False for other available positions, including credits/mixed adjustments or no cash; null without permission or original title. No new receipt, query per line or client-side balance formula.")
                                               Boolean receivableReceivedWithWithholding) {
    public static BillingInstallmentSummaryResponse from(ReadBillingInstallmentsUseCase.Summary result) {
        var value = result.installment();
        var position = result.receivableFinancialPosition();
        return new BillingInstallmentSummaryResponse(value.id(), value.contractId(), value.reference(), value.issuedOn(), value.dueOn(),
                value.status(), value.currency(), value.netAmount(), value.createdAt(), value.updatedAt(), position != null,
                position == null ? null : position.status(), position == null ? null : position.receivedWithWithholding());
    }
}
