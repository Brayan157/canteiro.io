package com.renovar.canteiro.io.measurements.api.response;

public record MeasurementSpreadsheetImportLineErrorResponse(int spreadsheetRow, String field, String message) {
}
