package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.receivables.application.ReadBillingInstallmentsUseCase;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationStatus;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationType;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Read-only official installment. Origin UUIDs preserve financial traceability; no measurement content, invoice or settlement balance is disclosed. Authorized financial status refers exclusively to the original title.",
        requiredProperties = {"id", "contractId", "dueOn", "status", "currency", "netAmount", "allocations", "receivableReferencesAvailable", "receivables", "createdAt", "updatedAt"})
public record BillingInstallmentDetailsResponse(UUID id, UUID contractId,
        @Schema(types = {"string", "null"}) String reference,
        @Schema(types = {"string", "null"}) String description,
        @Schema(types = {"string", "null"}, format = "date") LocalDate issuedOn,
        LocalDate dueOn, @Schema(description = "Persisted business status, not the current receipt status; use receivableFinancialStatus when available.") BillingInstallmentStatus status, CurrencyCode currency,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "140.00") BigDecimal netAmount,
        List<AllocationResponse> allocations,
        @Schema(description = "True only with RECEIVABLES.READ. When false the list is empty because access is restricted, not because no titles exist.") boolean receivableReferencesAvailable,
        List<ReceivableReferenceResponse> receivables, Instant createdAt, Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "True only when RECEIVABLES.READ is granted in addition to BILLING.READ and the installment original receivable has an official financial position. False means unavailable, never OPEN or zero.") boolean receivableFinancialStatusAvailable,
                                               @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                                               @Schema(types = {"string", "null"}, allowableValues = {"OPEN", "PARTIALLY_RECEIVED", "RECEIVED", "SETTLED_WITH_ADJUSTMENTS", "CANCELLED", "RENEGOTIATED"}, requiredMode = Schema.RequiredMode.REQUIRED,
                                                       description = "Official current status of the ORIGINAL receivable linked to this installment, using confirmed cash net of reversals, withholding and credits. Settled by adjustments is not cash RECEIVED. Preserves cancelled/renegotiated originals, does not aggregate children or imply settlement of an individual service/allocation. Null without permission or without an original title; no NF/date/client inference, and 202 proposals have no effect.")
                                               com.renovar.canteiro.io.receivables.domain.ReceivableFinancialPosition.Status receivableFinancialStatus,
                                               @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
                                               @Schema(types = {"boolean", "null"}, requiredMode = Schema.RequiredMode.REQUIRED,
                                                       description = "Presentation hint from the same authorized original-title financial position: true only for SETTLED_WITH_ADJUSTMENTS with zero outstanding, positive confirmed cash net of reversals, positive withholding and zero credits. UI may show Received with tax withholding. Does not classify withholding as cash or change financial status/amounts. False for other available positions, including credits/mixed adjustments or no cash; null without permission or original title. No new receipt, query per line or client-side balance formula.")
                                               Boolean receivableReceivedWithWithholding) {
    public static BillingInstallmentDetailsResponse from(ReadBillingInstallmentsUseCase.Details value) {
        var installment = value.installment();
        var position = value.receivableFinancialPosition();
        var lines = value.allocations().stream().map(line -> new AllocationResponse(line.allocation().getId(), line.allocation().getType(),
                line.allocation().getStatus(), line.allocation().getContractServiceId(), line.contractServiceName(),
                line.allocation().getMeasurementContractCompositionId(), line.allocation().getAllocatedAmount(),
                line.allocation().getNetBillingGrossAmount(), line.allocation().getNetBillingDiscountAmount())).toList();
        return new BillingInstallmentDetailsResponse(installment.getId(), installment.getContractId(), installment.getReference(),
                installment.getDescription(), installment.getIssuedOn(), installment.getDueOn(), installment.getStatus(), value.currency(),
                value.netAmount(), lines, value.receivableReferencesAvailable(), value.receivables().stream()
                .map(reference -> new ReceivableReferenceResponse(reference.id(), reference.reference())).toList(),
                installment.getCreatedAt(), installment.getUpdatedAt(), position != null,
                position == null ? null : position.status(), position == null ? null : position.receivedWithWithholding());
    }

    @Schema(name = "BillingInstallmentAllocationResponse", requiredProperties = {"allocationId", "type", "status", "allocatedAmount"})
    public record AllocationResponse(UUID allocationId, BillingAllocationType type, BillingAllocationStatus status,
            @Schema(types = {"string", "null"}, format = "uuid") UUID contractServiceId,
            @Schema(types = {"string", "null"}) String contractServiceName,
            @Schema(types = {"string", "null"}, format = "uuid") UUID measurementContractCompositionId,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(type = "string", format = "decimal", pattern = "^-?(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "-10.00") BigDecimal allocatedAmount,
            @JsonSerialize(using = CanonicalMoneySerializer.class) @Schema(types = {"string", "null"}, format = "decimal", example = "100.00") BigDecimal netBillingGrossAmount,
            @JsonSerialize(using = CanonicalMoneySerializer.class) @Schema(types = {"string", "null"}, format = "decimal", example = "10.00") BigDecimal netBillingDiscountAmount) {
    }
    @Schema(name = "BillingReceivableReferenceResponse", requiredProperties = "id")
    public record ReceivableReferenceResponse(UUID id, @Schema(types = {"string", "null"}) String reference) {
    }
}
