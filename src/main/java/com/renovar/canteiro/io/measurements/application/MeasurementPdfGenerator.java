package com.renovar.canteiro.io.measurements.application;

public interface MeasurementPdfGenerator {

    byte[] generate(MeasurementPdfData data);
}
