package com.renovar.canteiro.io.measurements.infrastructure.pdf;

import com.renovar.canteiro.io.measurements.application.MeasurementExportData;
import com.renovar.canteiro.io.measurements.application.MeasurementExportResult;
import com.renovar.canteiro.io.measurements.application.MeasurementItemSource;
import com.renovar.canteiro.io.measurements.application.MeasurementPdfGenerator;
import com.renovar.canteiro.io.measurements.infrastructure.reporting.StreamingMeasurementAccumulatedReportGenerator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;

/** Shares the landscape grid with accumulated reports without mixing historical versions. */
@Component
public class PdfBoxMeasurementPdfGenerator implements MeasurementPdfGenerator {
    @Override
    public MeasurementExportResult write(MeasurementExportData data, MeasurementItemSource items, OutputStream output)
            throws IOException {
        return new StreamingMeasurementAccumulatedReportGenerator().writeVersionPdf(data, items, output);
    }
}
