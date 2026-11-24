package com.renovar.canteiro.io.receivables.api.response;

import com.renovar.canteiro.io.measurements.domain.BillableMeasurementOrigin;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionOriginType;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Applied accepted measurement composition eligible for billing. Destination IDs are immutable; amounts are per origin, not a reservation or a replacement for contract/service limits.",
        requiredProperties = {"measurementContractCompositionId", "contractId", "contractServiceId", "measurementId",
                "measurementVersionId", "measurementServiceId", "measurementTitle", "serviceTitle", "contractServiceName",
                "versionNumber", "originType", "status", "currency", "acceptedAmount", "activeAllocatedAmount", "availableToBillAmount", "createdAt"})
public record BillableMeasurementOriginResponse(
        UUID measurementContractCompositionId, UUID contractId,
        @Schema(description = "Immutable destination recorded by this composition; never inferred from the current continuous-service destination.") UUID contractServiceId,
        UUID measurementId, UUID measurementVersionId, UUID measurementServiceId, UUID continuousServiceId,
        String measurementReference, String measurementTitle, String serviceTitle, String contractServiceName,
        int versionNumber, MeasurementCompositionOriginType originType,
        @Schema(allowableValues = "AVAILABLE", description = "Only applied, accepted, unreversed origins without unreconciled reductions and with positive remaining capacity are returned.") String status,
        CurrencyCode currency,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "100.00") BigDecimal acceptedAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "40.00") BigDecimal activeAllocatedAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", pattern = "^(0|[1-9][0-9]*)\\.[0-9]{2}$", example = "60.00") BigDecimal availableToBillAmount,
        Instant createdAt,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", description = "Gross origin capacity consumed, not only the net amount billed.") BigDecimal activeCapacityAmount,
        @JsonSerialize(using = CanonicalMoneySerializer.class)
        @Schema(type = "string", format = "decimal", description = "Official available net after the proportional uncommitted contract discount.") BigDecimal netAvailableToBillAmount
) {
    public static BillableMeasurementOriginResponse from(BillableMeasurementOrigin origin, CurrencyCode currency,
            com.renovar.canteiro.io.receivables.application.NetBillingCapacityService.State capacity) {
        return new BillableMeasurementOriginResponse(origin.measurementContractCompositionId(), origin.contractId(),
                origin.contractServiceId(), origin.measurementId(), origin.measurementVersionId(), origin.measurementServiceId(),
                origin.continuousServiceId(), origin.measurementReference(), origin.measurementTitle(), origin.serviceTitle(),
                origin.contractServiceName(), origin.versionNumber(), origin.originType(), "AVAILABLE", currency,
                origin.acceptedAmount(), origin.activeAllocatedAmount(), origin.availableToBillAmount(), origin.createdAt(),
                origin.acceptedAmount().subtract(origin.availableToBillAmount()), capacity.availableForOrigin(origin.measurementContractCompositionId()));
    }
}
