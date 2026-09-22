package com.renovar.canteiro.io.measurements.infrastructure.pdf;

import com.renovar.canteiro.io.measurements.application.MeasurementExportData;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxMeasurementPdfGeneratorTest {

    private final PdfBoxMeasurementPdfGenerator generator = new PdfBoxMeasurementPdfGenerator();

    @Test
    void generatesPdfWithMeasurementItemsAndAuditedTotals() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Measurement measurement = Measurement.create(companyId, UUID.randomUUID(), null, "Ago/2026", "Cobertura",
                LocalDate.of(2026, 8, 19));
        MeasurementVersion version = MeasurementVersion.create(companyId, UUID.randomUUID(), 1);
        MeasurementItem item = MeasurementItem.createKilogramPerSquareMeter(companyId, versionId, 1, "Telhado", "Área coberta",
                new BigDecimal("2.5000"), new BigDecimal("10.0000"), new BigDecimal("8.00"));
        MeasurementDiscount discount = MeasurementDiscount.create(companyId, versionId, MeasurementDiscountType.PERCENTAGE,
                new BigDecimal("10.0000"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var result = generator.write(new MeasurementExportData(measurement, version, discount),
                consumer -> consumer.accept(item), output);
        byte[] bytes = output.toByteArray();

        assertEquals(1, result.itemCount());
        assertTrue(bytes.length > 4);
        assertEquals((byte) '%', bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'D', bytes[2]);
        assertEquals((byte) 'F', bytes[3]);
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertEquals(1, document.getNumberOfPages());
            assertTrue(text.contains("MEDIÇÃO Ago/2026"));
            assertTrue(text.contains("Telhado"));
            assertTrue(text.contains("Área coberta"));
            assertTrue(text.contains("Total líquido"));
        }
    }
}
