package com.renovar.canteiro.io.measurements.application;

public interface MeasurementSpreadsheetGenerator {

    byte[] generate(MeasurementSpreadsheetData data);
}
