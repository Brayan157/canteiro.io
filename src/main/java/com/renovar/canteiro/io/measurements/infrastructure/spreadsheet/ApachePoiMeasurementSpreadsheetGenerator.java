package com.renovar.canteiro.io.measurements.infrastructure.spreadsheet;

import com.renovar.canteiro.io.measurements.application.MeasurementExportData;
import com.renovar.canteiro.io.measurements.application.MeasurementExportResult;
import com.renovar.canteiro.io.measurements.application.MeasurementItemSource;
import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetGenerator;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import com.renovar.canteiro.io.measurements.infrastructure.branding.MeasurementCompanyBrandingImage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.Objects;

import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.money;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.quantity;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.measure;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.factor;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.status;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.date;

/** Ten presentation columns plus stable, hidden, lossless import columns. */
@Component
public class ApachePoiMeasurementSpreadsheetGenerator implements MeasurementSpreadsheetGenerator {
    @Override
    public MeasurementExportResult write(MeasurementExportData data, MeasurementItemSource items, OutputStream output) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try (workbook) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Medição");
            sheet.setDisplayGridlines(false);
            CellStyle title = style(workbook, true, IndexedColors.DARK_TEAL, IndexedColors.WHITE, HorizontalAlignment.CENTER);
            CellStyle group = style(workbook, true, IndexedColors.LIGHT_BLUE, IndexedColors.BLACK, HorizontalAlignment.CENTER);
            CellStyle normal = style(workbook, false, IndexedColors.WHITE, IndexedColors.BLACK, HorizontalAlignment.LEFT);
            CellStyle number=workbook.createCellStyle();number.cloneStyleFrom(normal);number.setAlignment(HorizontalAlignment.RIGHT);
            CellStyle summary = style(workbook, true, IndexedColors.GREY_25_PERCENT, IndexedColors.BLACK, HorizontalAlignment.LEFT);
            int[] widths = {9, 22, 60, 14, 10, 18, 22, 18, 24, 24};
            for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);
            Row branding = sheet.createRow(0); branding.setHeightInPoints(44);
            var logo = data.companyBranding() == null ? null : MeasurementCompanyBrandingImage.normalize(data.companyBranding().logoContent()).orElse(null);
            if (logo != null) {
                int index = workbook.addPicture(logo.content(), Workbook.PICTURE_TYPE_PNG);
                ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor(); anchor.setCol1(0); anchor.setRow1(0);
                var picture = sheet.createDrawingPatriarch().createPicture(anchor, index);
                var image = javax.imageio.ImageIO.read(new ByteArrayInputStream(logo.content()));
                picture.resize(Math.min(170d / image.getWidth(), 50d / image.getHeight()));
            } else {
                band(sheet, 0, data.companyBranding() == null ? "Empresa emitente" : data.companyBranding().displayName(), group);
            }
            band(sheet, 1, data.measurement().getTitle(), title);
            band(sheet, 2, "Referência: " + display(data.measurement().getReference()) + " | Versão: " + data.version().getVersionNumber()
                    + " | " + status(data.version().getStatus().name()) + " | Moeda: " + data.measurement().getCurrency(), summary);
            band(sheet, 3, "Data da medição: " + date(data.measurement().getMeasuredOn()) + " | Aceite externo: " + date(data.version().getExternalAcceptanceOn()), normal);
            band(sheet, 4, "Obra: " + display(data.measurement().getWorkId()) + " | Contrato: " + display(data.measurement().getContractId()), normal);
            band(sheet, 5, "Observações do aceite: " + display(data.version().getExternalAcceptanceNotes()), normal);
            String symbol = data.measurement().getCurrency().getSymbol();
            String[] headers = {"Item", "Situação", "Atividade / descrição", "Data", "Und", "m ou m²", "kg/m ou kg/m²", "Peso (kg)",
                    "Preço (" + symbol + ")", "Valor (" + symbol + ")", "Nº", "Serviço", "Atividade", "Descrição", "Data (origem)", "Tipo",
                    "Área (m²)", "Metragem (m)", "Kg/m²", "Kg/m", "Peças/unid. executadas", "Preço unit. (decimal)", "Peso total (kg)",
                    "Valor total (decimal)", "Item ID", "Versão ID", "Medição ID", "Moeda", "Serviço contínuo ID"};
            Row header = sheet.createRow(8); header.setHeightInPoints(36);
            for (int c = 0; c < headers.length; c++) cell(header, c, headers[c], title);
            for (int c = 10; c < headers.length; c++) sheet.setColumnHidden(c, true);
            Sheet metadata = workbook.createSheet("_measurement_metadata");
            String[] values = {"measurement-xlsx-v2", display(data.measurement().getId()), display(data.version().getId()), data.measurement().getCurrency().name()};
            for (int n = 0; n < values.length; n++) cell(metadata.createRow(n), 0, values[n], normal);
            workbook.setSheetVisibility(1, SheetVisibility.VERY_HIDDEN);
            int[] next = {9}, count = {0}, first = {-1}, last = {-1};
            String[] service = {null}; BigDecimal[] sums = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
            Object[] previousService={null};
            items.consume(item -> {
                java.util.UUID identity=data.serviceIdentity(item.getId());
                Object key=identity==null?item.getServiceTitle():identity;
                if (!Objects.equals(previousService[0], key)) {
                    if (service[0] != null) total(sheet, next[0]++, "Subtotal bruto do serviço", money(sums[2], data.measurement().getCurrency()), summary);
                    band(sheet, next[0]++, "SERVIÇO: " + item.getServiceTitle(), group); service[0] = item.getServiceTitle(); sums[2] = BigDecimal.ZERO;
                    previousService[0]=key;
                }
                Row row = sheet.createRow(next[0]++); int excelRow = row.getRowNum() + 1;
                String description = item.getActivity() + (item.getDescription() == null ? "" : "\n" + item.getDescription());
                String[] visible = {data.version().getVersionNumber() + "." + item.getItemNumber(), status(data.version().getStatus().name()), description,
                        date(item.getMeasuredOn()), quantity(item.getUnitQuantity()), measure(item), factor(item), quantity(item.getTotalWeightKg()),
                        money(item.getUnitPrice(), data.measurement().getCurrency()), money(item.getTotalAmount(), data.measurement().getCurrency())};
                for (int c = 0; c < visible.length; c++) cell(row, c, visible[c], c>=4?number:normal);
                // Raw decimal strings are exact. Excel formulas below are preview-only, never imported as official totals.
                String[] raw = {Integer.toString(item.getItemNumber()), item.getServiceTitle(), item.getActivity(), display(item.getDescription()),
                        item.getMeasuredOn() == null ? "" : item.getMeasuredOn().toString(), item.getChargeType().name(), decimal(item.getAreaSquareMeters()),
                        decimal(item.getLinearMeters()), decimal(item.getKilogramsPerSquareMeter()), decimal(item.getKilogramsPerLinearMeter()),
                        decimal(item.getUnitQuantity()), decimal(item.getUnitPrice())};
                for (int c = 0; c < raw.length; c++) cell(row, c + 10, raw[c], normal);
                var weight = row.createCell(22);
                switch (item.getChargeType()) {
                    case KILOGRAM_PER_SQUARE_METER -> weight.setCellFormula("Q" + excelRow + "*S" + excelRow);
                    case KILOGRAM_PER_LINEAR_METER -> weight.setCellFormula("R" + excelRow + "*T" + excelRow);
                    default -> { }
                }
                String quantityColumn = switch (item.getChargeType()) {
                    case SQUARE_METER -> "Q"; case LINEAR_METER -> "R";
                    case KILOGRAM_PER_SQUARE_METER, KILOGRAM_PER_LINEAR_METER -> "W"; case UNIT -> "U";
                };
                row.createCell(23).setCellFormula(quantityColumn + excelRow + "*V" + excelRow);
                cell(row, 24, item.getId() == null ? "" : item.getId().toString(), normal);
                cell(row, 25, display(data.version().getId()), normal); cell(row, 26, display(data.measurement().getId()), normal);
                cell(row, 27, data.measurement().getCurrency().name(), normal);
                cell(row, 28, identity==null?"":identity.toString(), normal);
                row.setHeightInPoints(Math.min(409, Math.max(38, (description.length() / 52 + 2) * 13)));
                // Description is stored in full in the stable columns and in continuation presentation rows.
                if (description.length() > 1200) {
                    row.getCell(2).setCellValue(description.substring(0, 1200));
                    for (int start = 1200; start < description.length(); start += 1200) {
                        Row continuation = sheet.createRow(next[0]++);
                        cell(continuation, 2, description.substring(start, Math.min(start + 1200, description.length())), normal);
                        continuation.setHeightInPoints(338);
                    }
                }
                if (first[0] < 0) first[0] = excelRow; last[0] = excelRow;
                count[0]++; sums[0] = sums[0].add(item.getTotalAmount()); sums[2] = sums[2].add(item.getTotalAmount());
                if (item.getTotalWeightKg() != null) sums[1] = sums[1].add(item.getTotalWeightKg());
            });
            if (service[0] != null) total(sheet, next[0]++, "Subtotal bruto do serviço", money(sums[2], data.measurement().getCurrency()), summary);
            var amounts = MeasurementVersionAmountCalculator.calculate(sums[1], sums[0], data.discount());
            total(sheet, ++next[0], "Peso total (kg)", quantity(amounts.totalWeightKg()), summary);
            total(sheet, ++next[0], "Total bruto", money(amounts.grossAmount(), data.measurement().getCurrency()), summary);
            if (first[0] > 0) sheet.getRow(next[0]).createCell(23).setCellFormula("SUM(X" + first[0] + ":X" + last[0] + ")");
            total(sheet, ++next[0], "Desconto de cabeçalho", money(amounts.discountAmount(), data.measurement().getCurrency()), summary);
            total(sheet, ++next[0], "Total líquido", money(amounts.netAmount(), data.measurement().getCurrency()), title);
            sheet.createFreezePane(0, 9); sheet.setRepeatingRows(new CellRangeAddress(8, 8, 0, 9));
            sheet.getPrintSetup().setLandscape(true); sheet.setFitToPage(true); sheet.getPrintSetup().setFitWidth((short) 1);
            workbook.setPrintArea(0, 0, 9, 0, next[0]); workbook.setForceFormulaRecalculation(true);workbook.write(output); output.flush();
            return new MeasurementExportResult(count[0], amounts);
        } finally { workbook.dispose(); }
    }

    private static String decimal(BigDecimal value) { return value == null ? "" : value.toPlainString(); }
    private static String display(Object value) { return value == null ? "-" : value.toString(); }
    private static void cell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column); cell.setCellValue(value); cell.setCellStyle(style);
    }
    private static void band(Sheet sheet, int index, String label, CellStyle style) {
        Row row = sheet.getRow(index); if (row == null) row = sheet.createRow(index);
        cell(row, 0, label, style); sheet.addMergedRegion(new CellRangeAddress(index, index, 0, 9)); row.setHeightInPoints(30);
    }
    private static void total(Sheet sheet, int index, String label, String value, CellStyle style) {
        Row row = sheet.createRow(index); cell(row, 0, label, style); cell(row, 8, value, style);
        sheet.addMergedRegion(new CellRangeAddress(index, index, 0, 7)); sheet.addMergedRegion(new CellRangeAddress(index, index, 8, 9)); row.setHeightInPoints(26);
    }
    private static CellStyle style(Workbook workbook, boolean bold, IndexedColors fill, IndexedColors color, HorizontalAlignment alignment) {
        CellStyle style = workbook.createCellStyle(); style.setFillForegroundColor(fill.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(alignment); style.setVerticalAlignment(VerticalAlignment.CENTER); style.setWrapText(true);
        Font font = workbook.createFont(); font.setBold(bold); font.setColor(color.getIndex()); style.setFont(font); return style;
    }
}
