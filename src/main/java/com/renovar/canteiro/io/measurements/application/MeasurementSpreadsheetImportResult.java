package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;

import java.util.List;

public record MeasurementSpreadsheetImportResult(
        List<MeasurementItem> items,
        MeasurementVersion version,
        MeasurementVersionAmounts amounts,
        ChangeRequest changeRequest,
        ChangeAuthorizationMode authorizationMode
) {
}
