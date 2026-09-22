package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts;

/** Summary produced while rows are streamed into the document. */
public record MeasurementExportResult(int itemCount, MeasurementVersionAmounts amounts) {
}
