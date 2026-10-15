package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;

import java.util.List;
import java.util.UUID;

public record MeasurementSpreadsheetImportPreview(
        UUID measurementId,
        UUID measurementVersionId,
        String originalFilename,
        String spreadsheetHash,
        boolean valid,
        List<MeasurementSpreadsheetImportDifference> differences,
        List<MeasurementSpreadsheetImportLineError> lineErrors,
        MeasurementVersionAmounts amounts
) {
}
