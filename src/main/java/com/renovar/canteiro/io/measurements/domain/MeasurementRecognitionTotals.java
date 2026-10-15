package com.renovar.canteiro.io.measurements.domain;
import java.math.BigDecimal;
import java.util.UUID;
public record MeasurementRecognitionTotals(UUID continuousServiceId, long itemCount, BigDecimal grossAmount,
        BigDecimal netAmount, BigDecimal totalWeightKg) { }
