package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMeasurementItemCommand(
        UUID measurementId,
        UUID measurementVersionId,
        int itemNumber,
        String activity,
        String description,
        MeasurementChargeType chargeType,
        BigDecimal areaSquareMeters,
        BigDecimal linearMeters,
        BigDecimal kilogramsPerSquareMeter,
        BigDecimal kilogramsPerLinearMeter,
        BigDecimal unitPrice,
        String justification
) {
}
