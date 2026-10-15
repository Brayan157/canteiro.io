package com.renovar.canteiro.io.measurements.infrastructure.pdf;

import com.renovar.canteiro.io.measurements.application.MeasurementExportData;
import com.renovar.canteiro.io.measurements.application.MeasurementCompanyBranding;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
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
        Measurement measurement = Measurement.create(companyId, UUID.randomUUID(), null, "Medição da cobertura",
                "Ago/2026", "Cobertura", LocalDate.of(2026, 8, 19));
        MeasurementVersion version = MeasurementVersion.create(companyId, UUID.randomUUID(), 1);
        MeasurementItem item = MeasurementItem.createKilogramPerSquareMeter(companyId, versionId, 1, "Telhado", "Área coberta",
                new BigDecimal("2.5000"), new BigDecimal("10.0000"), new BigDecimal("8.00"));
        MeasurementItem otherServiceItem = MeasurementItem.createSquareMeter(companyId, versionId, 2,
                "Linha de álcool", "Atividade adicional", null, LocalDate.of(2026, 8, 19),
                new BigDecimal("2.0000"), new BigDecimal("25.00"));
        MeasurementDiscount discount = MeasurementDiscount.create(companyId, versionId, MeasurementDiscountType.PERCENTAGE,
                new BigDecimal("10.0000"));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var result = generator.write(new MeasurementExportData(measurement, version, discount),
                consumer -> {
                    consumer.accept(item);
                    consumer.accept(otherServiceItem);
                }, output);
        byte[] bytes = output.toByteArray();

        assertEquals(2, result.itemCount());
        assertTrue(bytes.length > 4);
        assertEquals((byte) '%', bytes[0]);
        assertEquals((byte) 'P', bytes[1]);
        assertEquals((byte) 'D', bytes[2]);
        assertEquals((byte) 'F', bytes[3]);
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertEquals(1, document.getNumberOfPages());
            assertTrue(text.contains("Medição da cobertura"));
            assertTrue(text.contains("Empresa emitente"));
            assertTrue(text.contains("SERVIÇO: Telhado"));
            assertTrue(text.contains("SERVIÇO: Linha de álcool"));
            assertTrue(text.contains("Telhado"));
            assertTrue(text.contains("Área coberta"));
            assertTrue(text.contains("Total líquido"));
        }
    }

    @Test
    void embedsPrivateCompanyLogoWhenBrandingContainsPng() throws Exception {
        UUID companyId = UUID.randomUUID();
        Measurement measurement = Measurement.create(companyId, UUID.randomUUID(), null, "Medição com logo",
                "AGO-2026", null, LocalDate.of(2026, 8, 19));
        MeasurementVersion version = MeasurementVersion.create(companyId, UUID.randomUUID(), 1);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        generator.write(new MeasurementExportData(measurement, version, null,
                        new MeasurementCompanyBranding("Emitente privado", pngLogo())), consumer -> { }, output);

        try (var document = Loader.loadPDF(output.toByteArray())) {
            assertTrue(hasImage(document));
            assertTrue(new PDFTextStripper().getText(document).contains("Medição com logo"));
        }
    }

    private boolean hasImage(org.apache.pdfbox.pdmodel.PDDocument document) throws java.io.IOException {
        for (COSName name : document.getPage(0).getResources().getXObjectNames()) {
            PDXObject object = document.getPage(0).getResources().getXObject(name);
            if (object instanceof PDImageXObject) {
                return true;
            }
        }
        return false;
    }

    private byte[] pngLogo() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF006D77);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
