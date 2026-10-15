package com.renovar.canteiro.io.measurements.application;

/** Safe validation feedback associated with an XLSX row. */
public record MeasurementSpreadsheetImportLineError(int spreadsheetRow, String field, String message) {
}
