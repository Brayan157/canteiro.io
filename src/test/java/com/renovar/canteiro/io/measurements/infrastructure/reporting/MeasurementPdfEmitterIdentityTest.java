package com.renovar.canteiro.io.measurements.infrastructure.reporting;

import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedTotalsResponse;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedLine;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedReportData;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedServiceSubtotal;
import com.renovar.canteiro.io.measurements.application.MeasurementCompanyBranding;
import com.renovar.canteiro.io.measurements.application.MeasurementExportData;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialSummary;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.measurements.infrastructure.pdf.PdfBoxMeasurementPdfGenerator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementPdfEmitterIdentityTest {
    private static final String COMPANY = "Engenharia Exemplo - dados fictícios de QA";
    private static final String TITLE = "Serviços industriais aprovados - amostra de QA";
    private final StreamingMeasurementAccumulatedReportGenerator generator = new StreamingMeasurementAccumulatedReportGenerator();

    @Test
    void printsRegisteredNameAndNumericCnpjWithLogoOnAccumulatedAndVersionPdfs() throws Exception {
        var sample = sample(COMPANY, "11222333000181", logo(), TITLE, 3);
        byte[] accumulated = pdf(sample, false);
        assertIdentityOnEveryPage(accumulated, sample, "CNPJ: 11.222.333/0001-81", true);
        byte[] version = pdf(sample, true);
        assertIdentityOnEveryPage(version, sample, "CNPJ: 11.222.333/0001-81", true);
        save("medicao-pdf-emitente-com-logo.pdf", accumulated);
        Files.createDirectories(Path.of("tmp", "pdfs"));
        Files.write(Path.of("tmp", "pdfs", "versao-pdf-emitente-com-logo.pdf"), version);
    }

    @Test
    void formatsTheRegisteredAlphanumericCnpjWithoutNeedingAnImage() throws Exception {
        var sample = sample(COMPANY, "12.ABC.345/01de-35", null, TITLE, 3);
        byte[] pdf = pdf(sample, true);
        assertIdentityOnEveryPage(pdf, sample, "CNPJ: 12.ABC.345/01DE-35", false);
        save("medicao-pdf-emitente-sem-logo.pdf", pdf);
    }

    @Test
    void repeatsFullLongNameAndMissingDocumentFallbackWithoutOverlappingTitleOrTable() throws Exception {
        String name = ("Empresa prestadora de fabricação e montagem industrial ".repeat(6)).substring(0, 255).trim();
        String title = ("Medição técnica consolidada de serviços industriais ".repeat(5)).substring(0, 200).trim();
        var sample = sample(name, null, logo(), title, 24);
        byte[] pdf = pdf(sample, false);
        assertIdentityOnEveryPage(pdf, sample, "CNPJ: não informado", true);
        try (var document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
        }
        save("medicao-pdf-emitente-cabecalho-longo.pdf", pdf);
    }

    @Test
    void incompatibleDocumentIsNotRelabeledOrFabricatedAsACnpj() throws Exception {
        var sample = sample(COMPANY, "FOREIGN-REGISTRY-123", new byte[]{1, 2, 3}, TITLE, 1);
        byte[] pdf = pdf(sample, false);
        assertIdentityOnEveryPage(pdf, sample, "CNPJ: não informado (documento cadastrado incompatível)", false);
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertFalse(text.contains("FOREIGN-REGISTRY-123"));
            assertFalse(text.contains("11.222.333/0001-81"));
        }
    }

    @Test
    void nullBrandingKeepsExplicitCompanyAndDocumentFallbacks() throws Exception {
        var sample = sample(COMPANY, null, null, TITLE, 1);
        var output = new ByteArrayOutputStream();
        new PdfBoxMeasurementPdfGenerator().write(new MeasurementExportData(sample.measurement(), sample.version(), null),
                consumer -> consumer.accept(sample.items().getFirst()), output);
        try (var document = Loader.loadPDF(output.toByteArray())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Empresa emitente não informada"));
            assertTrue(text.contains("CNPJ: não informado"));
        }
    }

    @Test
    void theAddedInternalDocumentDoesNotChangeSpreadsheetCellsOrExposeTheCnpj() throws Exception {
        var first = sample(COMPANY, "11222333000181", logo(), TITLE, 2);
        var second = new Sample(first.measurement(), first.version(), new MeasurementCompanyBranding(COMPANY,
                "12ABC34501DE35", first.branding().logoContent()), first.items());
        var before = new ByteArrayOutputStream();
        var after = new ByteArrayOutputStream();
        generator.writeSpreadsheet(data(first), consumer -> { for (var line : lines(first)) consumer.accept(line); }, before);
        generator.writeSpreadsheet(data(second), consumer -> { for (var line : lines(second)) consumer.accept(line); }, after);
        try (var a = WorkbookFactory.create(new ByteArrayInputStream(before.toByteArray()));
             var b = WorkbookFactory.create(new ByteArrayInputStream(after.toByteArray()))) {
            assertEquals(a.getAllPictures().size(), b.getAllPictures().size());
            assertEquals(a.getSheetAt(0).getLastRowNum(), b.getSheetAt(0).getLastRowNum());
            for (var row : a.getSheetAt(0)) for (var cell : row) {
                String value = cell.toString();
                assertEquals(value, b.getSheetAt(0).getRow(row.getRowNum()).getCell(cell.getColumnIndex()).toString());
                assertFalse(value.contains("11222333000181") || value.contains("12ABC34501DE35") || value.contains("CNPJ:"));
            }
        }
    }

    private byte[] pdf(Sample sample, boolean historical) throws Exception {
        var output = new ByteArrayOutputStream();
        if (historical) {
            new PdfBoxMeasurementPdfGenerator().write(new MeasurementExportData(sample.measurement(), sample.version(), null,
                    sample.branding()), consumer -> { for (var item : sample.items()) consumer.accept(item); }, output);
        } else {
            generator.writePdf(data(sample), consumer -> { for (var line : lines(sample)) consumer.accept(line); }, output);
        }
        return output.toByteArray();
    }

    private void assertIdentityOnEveryPage(byte[] bytes, Sample sample, String cnpj, boolean logo) throws Exception {
        try (var document = Loader.loadPDF(bytes)) {
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                var text = new HeaderText();
                text.setStartPage(page);
                text.setEndPage(page);
                String contents = text.getText(document).replaceAll("\\s+", " ");
                assertTrue(contents.contains(sample.branding().displayName()), contents);
                assertTrue(contents.contains(cnpj), contents);
                assertTrue(contents.contains(sample.measurement().getTitle()), contents);
                assertEquals(logo, document.getPage(page - 1).getResources().getXObjectNames().iterator().hasNext());
                if (logo) for (var key : document.getPage(page - 1).getResources().getXObjectNames()) {
                    assertTrue(document.getPage(page - 1).getResources().getXObject(key) instanceof PDImageXObject);
                }
                var documentLine = text.lines.stream().filter(line -> line.text().startsWith("CNPJ:")).findFirst().orElseThrow();
                var titleLine = text.lines.stream().filter(line -> line.text().contains(sample.measurement().getTitle().substring(0, 24)))
                        .findFirst().orElseThrow();
                var tableLine = text.lines.stream().filter(line -> line.text().contains("Atividade / descrição")).findFirst().orElseThrow();
                assertTrue(documentLine.y() + 8 < titleLine.y());
                assertTrue(titleLine.y() + 12 < tableLine.y());
                for (var line : text.lines) if (line.y() < tableLine.y()) {
                    assertTrue(line.left() >= 28 && line.right() <= document.getPage(page - 1).getMediaBox().getWidth() - 25,
                            "Header outside margins: " + line.text());
                }
            }
        }
    }

    private MeasurementAccumulatedReportData data(Sample sample) {
        BigDecimal amount = new BigDecimal("25.00").multiply(BigDecimal.valueOf(sample.items().size()));
        var accepted = MeasurementAccumulatedTotalsResponse.of(sample.items().size(), amount, amount, null);
        var zero = MeasurementAccumulatedTotalsResponse.of(0, BigDecimal.ZERO, BigDecimal.ZERO, null);
        var finance = new MeasurementFinancialSummary(sample.measurement().getId(), null, amount, null, null, null, null,
                List.of(), "NO_CONTRACT", Instant.parse("2026-10-15T15:00:00Z"), null, null, null);
        List<MeasurementAccumulatedServiceSubtotal> subtotals = new ArrayList<>();
        for (String title : List.of("Linha de Vinhaça", "Linha de cobre")) {
            int count = (int) sample.items().stream().filter(item -> title.equals(item.getServiceTitle())).count();
            if (count == 0) continue;
            BigDecimal subtotal = new BigDecimal("25.00").multiply(BigDecimal.valueOf(count));
            subtotals.add(new MeasurementAccumulatedServiceSubtotal(null, title,
                    MeasurementAccumulatedTotalsResponse.of(count, subtotal, subtotal, null), zero));
        }
        return new MeasurementAccumulatedReportData(sample.measurement(), sample.version(), sample.branding(), accepted, zero, finance, subtotals);
    }

    private List<MeasurementAccumulatedLine> lines(Sample sample) {
        return sample.items().stream().map(item -> new MeasurementAccumulatedLine(null, item.getServiceTitle(), item, 1, "ACCEPTED", "Aceito")).toList();
    }

    private Sample sample(String name, String document, byte[] logo, String title, int count) {
        UUID company = UUID.randomUUID(), mid = UUID.randomUUID(), vid = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T15:00:00Z");
        var measurement = Measurement.rehydrate(mid, company, null, null, title, "QA-2026", null,
                LocalDate.of(2026, 10, 15), MeasurementStatus.ACCEPTED, 0, now, now);
        var version = MeasurementVersion.rehydrate(vid, company, mid, 1, null, MeasurementVersionStatus.ACCEPTED, 0,
                LocalDate.of(2026, 10, 15), null, now, now);
        List<MeasurementItem> rows = new ArrayList<>();
        for (int index = 1; index <= count; index++) rows.add(MeasurementItem.createLinearMeter(company, vid, index,
                index <= count / 2 ? "Linha de Vinhaça" : "Linha de cobre", "Atividade de QA " + index,
                count > 10 ? "Descrição técnica preservada sem corte de conteúdo. ".repeat(4) : "Dados fictícios para conferência.",
                LocalDate.of(2026, 10, 15), BigDecimal.ONE, new BigDecimal("25.00")));
        return new Sample(measurement, version, new MeasurementCompanyBranding(name, document, logo), rows);
    }

    private byte[] logo() throws Exception {
        var image = new BufferedImage(160, 40, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(0, 105, 95));
        graphics.fillRect(0, 0, 160, 40);
        graphics.dispose();
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void save(String name, byte[] bytes) throws Exception {
        Files.createDirectories(Path.of("output", "pdf"));
        Files.write(Path.of("output", "pdf", name), bytes);
    }

    private record Sample(Measurement measurement, MeasurementVersion version, MeasurementCompanyBranding branding, List<MeasurementItem> items) { }
    private record Line(String text, float left, float right, float y) { }

    private static class HeaderText extends PDFTextStripper {
        private final List<Line> lines = new ArrayList<>();
        HeaderText() throws IOException { }
        @Override protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (!positions.isEmpty()) {
                float left = Float.MAX_VALUE, right = 0;
                for (var position : positions) {
                    left = Math.min(left, position.getXDirAdj());
                    right = Math.max(right, position.getXDirAdj() + position.getWidthDirAdj());
                }
                lines.add(new Line(text, left, right, positions.getFirst().getYDirAdj()));
            }
            super.writeString(text, positions);
        }
    }
}
