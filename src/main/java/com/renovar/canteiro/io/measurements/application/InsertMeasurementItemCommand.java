package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InsertMeasurementItemCommand(
        UUID measurementId,
        UUID measurementVersionId,
        UUID insertAfterItemId,
        String serviceTitle,
        String activity,
        String description,
        LocalDate measuredOn,
        MeasurementChargeType chargeType,
        BigDecimal areaSquareMeters,
        BigDecimal linearMeters,
        BigDecimal kilogramsPerSquareMeter,
        BigDecimal kilogramsPerLinearMeter,
        BigDecimal unitQuantity,
        BigDecimal unitPrice,
        BigDecimal previewTotalWeightKg,
        BigDecimal previewTotalAmount,
        String justification,
        UUID continuousServiceId
) {
    public InsertMeasurementItemCommand(
            UUID measurementId,
            UUID measurementVersionId,
            UUID insertAfterItemId,
            String serviceTitle,
            String activity,
            String description,
            LocalDate measuredOn,
            MeasurementChargeType chargeType,
            BigDecimal areaSquareMeters,
            BigDecimal linearMeters,
            BigDecimal kilogramsPerSquareMeter,
            BigDecimal kilogramsPerLinearMeter,
            BigDecimal unitQuantity,
            BigDecimal unitPrice,
            BigDecimal previewTotalWeightKg,
            BigDecimal previewTotalAmount,
            String justification
    ) { this(measurementId, measurementVersionId, insertAfterItemId, serviceTitle, activity, description, measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter, kilogramsPerLinearMeter, unitQuantity, unitPrice, previewTotalWeightKg, previewTotalAmount, justification, null); }
}
