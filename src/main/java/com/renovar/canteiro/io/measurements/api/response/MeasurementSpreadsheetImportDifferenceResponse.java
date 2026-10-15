package com.renovar.canteiro.io.measurements.api.response;

import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetImportDifferenceType;

import java.util.UUID;

public record MeasurementSpreadsheetImportDifferenceResponse(
        int spreadsheetRow,
        UUID itemId,
        MeasurementSpreadsheetImportDifferenceType type,
        String serviceTitle,
        String activity
) {
}
