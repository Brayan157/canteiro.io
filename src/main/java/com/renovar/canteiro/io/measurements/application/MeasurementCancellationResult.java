package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record MeasurementCancellationResult(
        ChangeRequest changeRequest,
        ChangeAuthorizationMode authorizationMode
) {
}
