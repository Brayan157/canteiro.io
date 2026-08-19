package com.renovar.canteiro.io.measurements.api.request;

import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateMeasurementDiscountRequest(
        @NotNull MeasurementDiscountType discountType,
        @NotNull @Positive BigDecimal discountValue,
        @Size(max = 2000) String justification
) {
}
