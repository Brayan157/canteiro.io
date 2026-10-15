package com.renovar.canteiro.io.measurements.application;

import java.util.UUID;

/** A safe, line-oriented summary of a pending XLSX synchronization. */
public record MeasurementSpreadsheetImportDifference(
        int spreadsheetRow,
        UUID itemId,
        MeasurementSpreadsheetImportDifferenceType type,
        String serviceTitle,
        String activity
) {
}
