package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;

public record MeasurementDiscountChangeResult(
        MeasurementDiscount measurementDiscount,
        MeasurementVersionAmounts amounts,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode authorizationMode
) {
}
