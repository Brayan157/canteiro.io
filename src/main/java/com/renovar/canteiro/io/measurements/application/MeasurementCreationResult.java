package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;

public record MeasurementCreationResult(
        Measurement measurement,
        MeasurementVersion version,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode authorizationMode
) {
}
