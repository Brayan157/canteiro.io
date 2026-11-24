package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read-only contractual origin; the destination belongs to the immutable composition. */
public record BillableMeasurementOrigin(
        UUID measurementContractCompositionId, UUID contractId, UUID contractServiceId,
        UUID measurementId, UUID measurementVersionId, UUID measurementServiceId, UUID continuousServiceId,
        String measurementReference, String measurementTitle, String serviceTitle, String contractServiceName,
        int versionNumber, MeasurementCompositionOriginType originType,
        BigDecimal acceptedAmount, BigDecimal activeAllocatedAmount, BigDecimal availableToBillAmount,
        Instant createdAt
) {
}
