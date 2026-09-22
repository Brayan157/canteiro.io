package com.renovar.canteiro.io.measurements.infrastructure.pdf;

import com.renovar.canteiro.io.measurements.application.MeasurementExportData;
import com.renovar.canteiro.io.measurements.application.MeasurementExportResult;
import com.renovar.canteiro.io.measurements.application.MeasurementItemSource;
import com.renovar.canteiro.io.measurements.application.MeasurementPdfGenerator;
import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class PdfBoxMeasurementPdfGenerator implements MeasurementPdfGenerator {
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDRectangle LANDSCAPE_A4 = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float LEFT = 32;
    private static final float TOP = 555;
    private static final float BOTTOM = 38;
    private static final float ROW_HEIGHT = 17;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final NumberFormat QUANTITY = NumberFormat.getNumberInstance(new Locale("pt", "BR"));
    private static final Column[] COLUMNS = {
            new Column("Nº", 24), new Column("Atividade", 100), new Column("Descrição", 135), new Column("Tipo", 72),
            new Column("Área", 45), new Column("Metros", 45), new Column("Kg/m²", 42), new Column("Kg/m", 42),
            new Column("Preço", 60), new Column("Peso kg", 55), new Column("Total", 65)
    };

    static {
        QUANTITY.setMinimumFractionDigits(0);
        QUANTITY.setMaximumFractionDigits(4);
    }

    @Override
    public MeasurementExportResult write(MeasurementExportData data, MeasurementItemSource items, OutputStream output)
            throws IOException {
        try (PDDocument document = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            RenderState state = addPage(document, 1, data);
            RenderStateHolder stateHolder = new RenderStateHolder(state);
            ItemSummary summary = new ItemSummary();
            items.consume(item -> {
                if (stateHolder.state.y() - ROW_HEIGHT < BOTTOM + 65) {
                    closePage(stateHolder.state);
                    stateHolder.state = addPage(document, stateHolder.state.pageNumber() + 1, data);
                }
                drawItem(stateHolder.state, item);
                stateHolder.state = stateHolder.state.withY(stateHolder.state.y() - ROW_HEIGHT);
                summary.add(item);
            });
            if (stateHolder.state.y() - (ROW_HEIGHT * 3) < BOTTOM) {
                closePage(stateHolder.state);
                stateHolder.state = addPage(document, stateHolder.state.pageNumber() + 1, data);
            }
            var amounts = MeasurementVersionAmountCalculator.calculate(summary.grossAmount(), data.discount());
            drawTotals(stateHolder.state, data.discount(), amounts);
            closePage(stateHolder.state);
            document.save(output);
            output.flush();
            return new MeasurementExportResult(summary.itemCount(), amounts);
        }
    }

    private RenderState addPage(PDDocument document, int pageNumber, MeasurementExportData data) throws IOException {
        PDPage page = new PDPage(LANDSCAPE_A4);
        document.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(document, page);
        drawText(stream, BOLD, 17, LEFT, TOP, "MEDIÇÃO " + display(data.measurement().getReference(), display(data.measurement().getId())));
        drawText(stream, REGULAR, 9, LEFT, TOP - 22, "Obra: " + data.measurement().getWorkId());
        drawText(stream, REGULAR, 9, LEFT + 270, TOP - 22, "Contrato: " + display(data.measurement().getContractId()));
        drawText(stream, REGULAR, 9, LEFT + 500, TOP - 22, "Versão: " + data.version().getVersionNumber());
        drawText(stream, REGULAR, 9, LEFT, TOP - 37, "Data da medição: " + formatDate(data.measurement().getMeasuredOn()));
        drawText(stream, REGULAR, 9, LEFT + 270, TOP - 37, "Status: " + data.version().getStatus().name());
        drawText(stream, REGULAR, 9, LEFT + 500, TOP - 37, "Aceite externo: " + formatDate(data.version().getExternalAcceptanceOn()));
        drawTableHeader(stream, TOP - 60);
        return new RenderState(stream, TOP - 77, pageNumber);
    }

    private void drawTableHeader(PDPageContentStream stream, float y) throws IOException {
        float x = LEFT;
        for (Column column : COLUMNS) {
            fill(stream, x, y - 4, column.width(), ROW_HEIGHT, 0.0f, 0.41f, 0.36f);
            drawText(stream, BOLD, 7.5f, x + 3, y + 2, column.label());
            x += column.width();
        }
    }

    private void drawItem(RenderState state, MeasurementItem item) throws IOException {
        float x = LEFT;
        String[] values = {Integer.toString(item.getItemNumber()), truncate(item.getActivity(), 22),
                truncate(item.getDescription(), 31), chargeTypeLabel(item.getChargeType()), quantity(item.getAreaSquareMeters()),
                quantity(item.getLinearMeters()), quantity(item.getKilogramsPerSquareMeter()),
                quantity(item.getKilogramsPerLinearMeter()), currency(item.getUnitPrice()), quantity(item.getTotalWeightKg()),
                currency(item.getTotalAmount())};
        for (int index = 0; index < COLUMNS.length; index++) {
            stroke(state.stream(), x, state.y() - 4, COLUMNS[index].width(), ROW_HEIGHT);
            drawText(state.stream(), REGULAR, 7.5f, x + 3, state.y() + 2, values[index]);
            x += COLUMNS[index].width();
        }
    }

    private void drawTotals(RenderState state, com.renovar.canteiro.io.measurements.domain.MeasurementDiscount discount,
                            com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmounts amounts) throws IOException {
        float x = LEFT + 500;
        float y = state.y() - 4;
        drawSummaryRow(state.stream(), x, y, "Total bruto", currency(amounts.grossAmount()), false);
        drawSummaryRow(state.stream(), x, y - ROW_HEIGHT, "Desconto de cabeçalho", currency(amounts.discountAmount()), false);
        String discountDescription = discount == null ? "Sem desconto" : discount.getDiscountType() == MeasurementDiscountType.PERCENTAGE
                ? discount.getDiscountValue().stripTrailingZeros().toPlainString() + "%" : currency(discount.getDiscountValue());
        drawText(state.stream(), REGULAR, 7, x, y - (ROW_HEIGHT * 2) - 3, discountDescription);
        drawSummaryRow(state.stream(), x, y - (ROW_HEIGHT * 3), "Total líquido", currency(amounts.netAmount()), true);
    }

    private void drawSummaryRow(PDPageContentStream stream, float x, float y, String label, String amount, boolean highlight)
            throws IOException {
        if (highlight) fill(stream, x, y - 4, 238, ROW_HEIGHT, 0.0f, 0.41f, 0.36f);
        else fill(stream, x, y - 4, 238, ROW_HEIGHT, 0.9f, 0.9f, 0.9f);
        drawText(stream, BOLD, 8, x + 4, y + 2, label);
        drawText(stream, BOLD, 8, x + 158, y + 2, amount);
    }

    private void closePage(RenderState state) throws IOException {
        drawText(state.stream(), REGULAR, 7, LEFT, 22, "Documento gerado pelo Canteiro.io - página " + state.pageNumber());
        state.stream().close();
    }

    private void fill(PDPageContentStream stream, float x, float y, float width, float height, float red, float green, float blue)
            throws IOException {
        stream.setNonStrokingColor(red, green, blue);
        stream.addRect(x, y, width, height);
        stream.fill();
        stream.setNonStrokingColor(0, 0, 0);
    }

    private void stroke(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.setStrokingColor(0.82f, 0.82f, 0.82f);
        stream.addRect(x, y, width, height);
        stream.stroke();
        stream.setStrokingColor(0, 0, 0);
    }

    private void drawText(PDPageContentStream stream, PDFont font, float size, float x, float y, String value) throws IOException {
        stream.beginText(); stream.setFont(font, size); stream.newLineAtOffset(x, y); stream.showText(value == null ? "-" : value); stream.endText();
    }

    private String formatDate(java.time.LocalDate date) { return date == null ? "-" : DATE_FORMAT.format(date); }
    private String currency(BigDecimal value) { return value == null ? "-" : CURRENCY.format(value); }
    private String quantity(BigDecimal value) { return value == null ? "-" : QUANTITY.format(value); }
    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "-";
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }
    private String display(Object value) { return value == null ? "Não vinculado" : value.toString(); }
    private String display(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private String chargeTypeLabel(MeasurementChargeType chargeType) {
        return switch (chargeType) {
            case SQUARE_METER -> "m²";
            case LINEAR_METER -> "Metro linear";
            case KILOGRAM_PER_SQUARE_METER -> "kg/m²";
            case KILOGRAM_PER_LINEAR_METER -> "kg/metro linear";
        };
    }

    private record Column(String label, float width) { }
    private record RenderState(PDPageContentStream stream, float y, int pageNumber) {
        RenderState withY(float nextY) { return new RenderState(stream, nextY, pageNumber); }
    }
    private static final class RenderStateHolder {
        private RenderState state;

        private RenderStateHolder(RenderState state) {
            this.state = state;
        }
    }
    private static final class ItemSummary {
        private int itemCount;
        private BigDecimal grossAmount = BigDecimal.ZERO;

        void add(MeasurementItem item) {
            itemCount++;
            grossAmount = grossAmount.add(item.getTotalAmount());
        }

        int itemCount() { return itemCount; }
        BigDecimal grossAmount() { return grossAmount; }
    }
}
