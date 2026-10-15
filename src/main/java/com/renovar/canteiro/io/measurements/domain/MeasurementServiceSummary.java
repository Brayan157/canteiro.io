package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;

/** Official per-service aggregation of the active lines in one measurement version. */
public record MeasurementServiceSummary(
        String serviceTitle,
        int itemCount,
        BigDecimal grossAmount,
        BigDecimal totalWeightKg
) {
}
