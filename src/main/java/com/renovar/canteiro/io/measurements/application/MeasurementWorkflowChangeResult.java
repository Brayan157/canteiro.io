package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;

public record MeasurementWorkflowChangeResult(
        Measurement measurement,
        MeasurementVersion measurementVersion,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode authorizationMode
) {
}
