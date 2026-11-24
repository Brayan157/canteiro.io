package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Frozen composition identity without loading its item collection. */
public record MeasurementContractFinancialOrigin(UUID id, UUID contractServiceId, UUID measurementId,
        UUID measurementVersionId, UUID measurementServiceId, UUID continuousServiceId, BigDecimal netAmount, boolean eligible,
        java.time.LocalDate acceptedOn) {
}
