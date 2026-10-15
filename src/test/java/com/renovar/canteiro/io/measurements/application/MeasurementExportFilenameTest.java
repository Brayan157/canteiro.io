package com.renovar.canteiro.io.measurements.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeasurementExportFilenameTest {

    @Test
    void derivesAsciiFilenamesFromTheMeasurementTitle() {
        assertEquals("titulo-da-medicao.pdf", MeasurementExportFilename.pdf("Título da medição"));
        assertEquals("titulo-da-medicao.xlsx", MeasurementExportFilename.spreadsheet("Título da medição"));
    }

    @Test
    void removesUnsafeFilenameCharactersAndKeepsOnlyTheExpectedExtension() {
        assertEquals("medicao-obras-2026.pdf", MeasurementExportFilename.pdf("Medição / Obras: 2026.pdf"));
        assertEquals("medicao-obras-2026.xlsx", MeasurementExportFilename.spreadsheet("Medição / Obras: 2026.pdf"));
    }

    @Test
    void usesSafeFallbackForMissingOrReservedTitles() {
        assertEquals("medicao.pdf", MeasurementExportFilename.pdf(null));
        assertEquals("medicao.xlsx", MeasurementExportFilename.spreadsheet("CON"));
    }
}
