package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateMeasurementItemRequest(
        @Positive int itemNumber,
        @NotBlank @Size(max = 255) String activity,
        @Size(max = 1000) String description,
        @NotNull MeasurementChargeType chargeType,
        BigDecimal areaSquareMeters,
        BigDecimal linearMeters,
        BigDecimal kilogramsPerSquareMeter,
        BigDecimal kilogramsPerLinearMeter,
        BigDecimal unitPrice,
        @Size(max = 2000) String justification
) {
}
