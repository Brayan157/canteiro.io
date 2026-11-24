package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

public record BillingAllocationControlResponse(UUID allocationId, UUID billingInstallmentId, String installmentReference,
                                               LocalDate installmentDueOn,
                                               @Schema(description = "Persisted installment business/historical status. Does not establish the current receipt position; use receivableFinancialStatus when available.") BillingInstallmentStatus installmentStatus,
                                               UUID contractServiceId, String serviceName, BillingAllocationType type,
                                               BillingAllocationStatus status,
                                               @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String allocatedAmount,
                                               @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String registeredInvoiceAmount,
                                               @Schema(type = "string", format = "decimal", example = "99.90", pattern = "^-?\\d+\\.\\d{2}$") String availableInvoiceAmount,
                                               @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True only when RECEIVABLES.READ is granted in addition to BILLING.READ and the installment original receivable has an official financial position. False means unavailable, never OPEN or zero.") boolean receivableFinancialStatusAvailable,
                                               @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                                               @Schema(types = {"string", "null"}, allowableValues = {"OPEN", "PARTIALLY_RECEIVED", "RECEIVED", "SETTLED_WITH_ADJUSTMENTS", "CANCELLED", "RENEGOTIATED"}, requiredMode = Schema.RequiredMode.REQUIRED,
                                                       description = "Official current status of the ORIGINAL receivable linked to this installment, using confirmed cash net of reversals, withholding and credits. Settled by adjustments is not cash RECEIVED. Preserves cancelled/renegotiated originals, does not aggregate children or imply settlement of an individual service/allocation. Repeated on allocations of the same installment. Null without permission or without an original title; no NF/date/client inference, and 202 proposals have no effect.")
                                               com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition.Status receivableFinancialStatus,
                                               @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                                               @Schema(types = {"boolean", "null"}, requiredMode = Schema.RequiredMode.REQUIRED,
                                                       description = "Presentation hint from the same authorized original-title financial position: true only for SETTLED_WITH_ADJUSTMENTS with zero outstanding, positive confirmed cash net of reversals, positive withholding and zero credits. UI may show Received with tax withholding. Does not classify withholding as cash or change financial status/amounts. False for other available positions, including credits/mixed adjustments or no cash; null without permission or original title. No new receipt, query per line or client-side balance formula.")
                                               Boolean receivableReceivedWithWithholding) {
}
