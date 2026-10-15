package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;

import java.util.List;

public record MeasurementRevisionChangeResult(
        Measurement measurement,
        MeasurementVersion measurementVersion,
        List<MeasurementItem> items,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode authorizationMode
) {
}
