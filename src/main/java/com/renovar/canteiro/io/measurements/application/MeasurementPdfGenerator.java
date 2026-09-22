package com.renovar.canteiro.io.measurements.application;

import java.io.IOException;
import java.io.OutputStream;

public interface MeasurementPdfGenerator {

    MeasurementExportResult write(MeasurementExportData data, MeasurementItemSource items, OutputStream outputStream)
            throws IOException;
}
