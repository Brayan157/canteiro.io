package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;

import java.math.BigDecimal;
import java.util.UUID;

public record MeasurementDiscountChangeResponse(
        UUID discountId,
        MeasurementDiscountType discountType,
        BigDecimal discountValue,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal netAmount,
        UUID changeRequestId,
        ChangeAuthorizationMode authorizationMode
) {
}
