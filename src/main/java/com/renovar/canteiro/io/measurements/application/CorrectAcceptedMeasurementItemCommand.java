package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemCorrectionAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CorrectAcceptedMeasurementItemCommand(
        UUID measurementId,
        UUID measurementVersionId,
        UUID itemId,
        MeasurementItemCorrectionAction action,
        Integer itemNumber,
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
}
