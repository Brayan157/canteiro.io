package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateMeasurementItemCommand(
        UUID measurementId,
        UUID measurementVersionId,
        int itemNumber,
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
    public CreateMeasurementItemCommand(
            UUID measurementId,
            UUID measurementVersionId,
            int itemNumber,
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
    ) {
        this(measurementId, measurementVersionId, itemNumber, serviceTitle, activity, description, measuredOn, chargeType, areaSquareMeters, linearMeters, kilogramsPerSquareMeter, kilogramsPerLinearMeter, unitQuantity, unitPrice, previewTotalWeightKg, previewTotalAmount, justification, null);
    }
}
