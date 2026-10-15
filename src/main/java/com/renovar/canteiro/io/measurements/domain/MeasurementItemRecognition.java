package com.renovar.canteiro.io.measurements.domain;
import java.math.BigDecimal;
import java.util.UUID;
/** Immutable external acceptance position. Later versions append a new position for the same root. */
public record MeasurementItemRecognition(UUID companyId, UUID measurementId, UUID measurementVersionId,
        UUID measurementItemId, UUID rootItemId, UUID continuousServiceId, BigDecimal grossAmount,
        BigDecimal netAmount, BigDecimal totalWeightKg, boolean active, int versionNumber) { }

