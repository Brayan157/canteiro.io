package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;

public record MeasurementItemInsertionResult(
        MeasurementItem item,
        MeasurementVersion version,
        MeasurementVersionAmounts amounts,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode authorizationMode
) {
}
