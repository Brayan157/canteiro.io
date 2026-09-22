package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;

/** Immutable header data shared by streamed measurement document writers. */
public record MeasurementExportData(Measurement measurement, MeasurementVersion version, MeasurementDiscount discount) {
}
