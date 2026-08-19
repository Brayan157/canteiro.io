package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;

public record MeasurementItemChangeResult(
        MeasurementItem item,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode authorizationMode
) {
}
