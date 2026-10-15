package com.renovar.canteiro.io.measurements.application;
import java.io.IOException;
import java.io.OutputStream;
public interface MeasurementAccumulatedReportGenerator {
 long writePdf(MeasurementAccumulatedReportData data,MeasurementAccumulatedLineSource rows,OutputStream output) throws IOException;
 long writeSpreadsheet(MeasurementAccumulatedReportData data,MeasurementAccumulatedLineSource rows,OutputStream output) throws IOException;
}

