package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMeasurementDiscountCommand(
        UUID measurementVersionId,
        MeasurementDiscountType discountType,
        BigDecimal discountValue,
        String justification
) {
}
