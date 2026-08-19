package com.renovar.canteiro.io.measurements.infrastructure.spreadsheet;

import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetData;
import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetGenerator;
import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class ApachePoiMeasurementSpreadsheetGenerator implements MeasurementSpreadsheetGenerator {

    private static final String CURRENCY_FORMAT = "R$ #,##0.00";
    private static final String QUANTITY_FORMAT = "#,##0.0000";

    @Override
    public byte[] generate(MeasurementSpreadsheetData data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Medição");
            sheet.setDisplayGridlines(false);

            Styles styles = Styles.create(workbook);
            writeHeader(sheet, data, styles);
            int itemHeaderRow = 8;
            writeItemHeader(sheet, itemHeaderRow, styles);
            int firstItemRow = itemHeaderRow + 1;
            writeItems(sheet, firstItemRow, data.items(), styles);
            writeTotals(sheet, firstItemRow, data.items().size(), data, styles);
            configureColumns(sheet);
            sheet.createFreezePane(0, firstItemRow);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate measurement spreadsheet", exception);
        }
    }

    private void writeHeader(Sheet sheet, MeasurementSpreadsheetData data, Styles styles) {
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 11));
        Row titleRow = sheet.createRow(0);
        Cell title = titleRow.createCell(0);
        title.setCellValue("Medição " + display(data.measurement().getReference(), displayId(data.measurement().getId())));
        title.setCellStyle(styles.title());
        titleRow.setHeightInPoints(28);

        writeLabelValue(sheet, 2, 0, "Obra", data.measurement().getWorkId().toString(), styles);
        writeLabelValue(sheet, 2, 3, "Contrato", displayId(data.measurement().getContractId()), styles);
        writeLabelValue(sheet, 2, 6, "Data da medição", data.measurement().getMeasuredOn(), styles);
        writeLabelValue(sheet, 2, 9, "Status", data.version().getStatus().name(), styles);
        writeLabelValue(sheet, 4, 0, "Versão", data.version().getVersionNumber(), styles);
        writeLabelValue(sheet, 4, 3, "Aceite externo", data.version().getExternalAcceptanceOn(), styles);
        writeLabelValue(sheet, 4, 6, "Observações", display(data.version().getExternalAcceptanceNotes(), "-"), styles);
        writeLabelValue(sheet, 4, 9, "Desconto", discountDescription(data), styles);
    }

    private void writeLabelValue(Sheet sheet, int rowNumber, int column, String label, Object value, Styles styles) {
        Row row = row(sheet, rowNumber);
        Cell labelCell = row.createCell(column);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label());
        Cell valueCell = row.createCell(column + 1);
        if (value instanceof LocalDate localDate) {
            valueCell.setCellValue(localDate);
            valueCell.setCellStyle(styles.date());
        } else if (value instanceof Integer integer) {
            valueCell.setCellValue(integer);
            valueCell.setCellStyle(styles.value());
        } else {
            valueCell.setCellValue(value == null ? "-" : value.toString());
            valueCell.setCellStyle(styles.value());
        }
    }

    private void writeItemHeader(Sheet sheet, int rowNumber, Styles styles) {
        String[] headers = {"Nº", "Atividade", "Descrição", "Tipo", "Área (m²)", "Metragem (m)",
                "Kg/m²", "Kg/m", "Preço unit. (R$)", "Peso total (kg)", "Valor total (R$)", "Fórmula"};
        Row row = sheet.createRow(rowNumber);
        for (int column = 0; column < headers.length; column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(headers[column]);
            cell.setCellStyle(styles.tableHeader());
        }
        row.setHeightInPoints(28);
    }

    private void writeItems(Sheet sheet, int firstRow, List<MeasurementItem> items, Styles styles) {
        for (int index = 0; index < items.size(); index++) {
            MeasurementItem item = items.get(index);
            int rowNumber = firstRow + index;
            Row row = sheet.createRow(rowNumber);
            writeNumber(row, 0, BigDecimal.valueOf(item.getItemNumber()), styles.integer());
            writeText(row, 1, item.getActivity(), styles.text());
            writeText(row, 2, item.getDescription(), styles.text());
            writeText(row, 3, chargeTypeLabel(item.getChargeType()), styles.text());
            writeNumber(row, 4, item.getAreaSquareMeters(), styles.quantity());
            writeNumber(row, 5, item.getLinearMeters(), styles.quantity());
            writeNumber(row, 6, item.getKilogramsPerSquareMeter(), styles.quantity());
            writeNumber(row, 7, item.getKilogramsPerLinearMeter(), styles.quantity());
            writeNumber(row, 8, item.getUnitPrice(), styles.currency());
            writeWeightFormula(row, item.getChargeType(), rowNumber + 1, styles.quantity());
            writeAmountFormula(row, item.getChargeType(), rowNumber + 1, styles.currency());
            writeText(row, 11, item.getCalculationFormula(), styles.formula());
        }
    }

    private void writeWeightFormula(Row row, MeasurementChargeType chargeType, int excelRow, CellStyle style) {
        Cell cell = row.createCell(9);
        if (chargeType == MeasurementChargeType.KILOGRAM_PER_SQUARE_METER) {
            cell.setCellFormula("E" + excelRow + "*G" + excelRow);
        } else if (chargeType == MeasurementChargeType.KILOGRAM_PER_LINEAR_METER) {
            cell.setCellFormula("F" + excelRow + "*H" + excelRow);
        }
        cell.setCellStyle(style);
    }

    private void writeAmountFormula(Row row, MeasurementChargeType chargeType, int excelRow, CellStyle style) {
        Cell cell = row.createCell(10);
        String quantityColumn = switch (chargeType) {
            case SQUARE_METER -> "E";
            case LINEAR_METER -> "F";
            case KILOGRAM_PER_SQUARE_METER, KILOGRAM_PER_LINEAR_METER -> "J";
        };
        cell.setCellFormula(quantityColumn + excelRow + "*I" + excelRow);
        cell.setCellStyle(style);
    }

    private void writeTotals(Sheet sheet, int firstItemRow, int itemCount, MeasurementSpreadsheetData data, Styles styles) {
        int grossRow = firstItemRow + itemCount + 1;
        int discountRow = grossRow + 1;
        int netRow = grossRow + 2;
        int firstExcelRow = firstItemRow + 1;
        int lastExcelRow = firstItemRow + itemCount;

        writeTotalLabel(sheet, grossRow, "Total bruto", styles.total());
        Cell grossAmount = row(sheet, grossRow).createCell(10);
        if (itemCount == 0) {
            grossAmount.setCellValue(0);
        } else {
            grossAmount.setCellFormula("SUM(K" + firstExcelRow + ":K" + lastExcelRow + ")");
        }
        grossAmount.setCellStyle(styles.totalCurrency());

        writeTotalLabel(sheet, discountRow, "Desconto de cabeçalho", styles.total());
        Cell discountAmount = row(sheet, discountRow).createCell(10);
        if (data.discount() == null) {
            discountAmount.setCellValue(0);
        } else if (data.discount().getDiscountType() == MeasurementDiscountType.PERCENTAGE) {
            discountAmount.setCellFormula("K" + (grossRow + 1) + "*" + data.discount().getDiscountValue().toPlainString() + "/100");
        } else {
            discountAmount.setCellValue(data.discount().getDiscountValue().doubleValue());
        }
        discountAmount.setCellStyle(styles.totalCurrency());

        writeTotalLabel(sheet, netRow, "Total líquido", styles.netTotal());
        Cell netAmount = row(sheet, netRow).createCell(10);
        netAmount.setCellFormula("K" + (grossRow + 1) + "-K" + (discountRow + 1));
        netAmount.setCellStyle(styles.netTotalCurrency());
    }

    private void writeTotalLabel(Sheet sheet, int rowNumber, String label, CellStyle style) {
        Cell cell = row(sheet, rowNumber).createCell(9);
        cell.setCellValue(label);
        cell.setCellStyle(style);
    }

    private void configureColumns(Sheet sheet) {
        int[] widths = {8, 22, 30, 20, 15, 16, 12, 12, 18, 18, 18, 52};
        for (int column = 0; column < widths.length; column++) {
            sheet.setColumnWidth(column, widths[column] * 256);
        }
    }

    private void writeNumber(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private Row row(Sheet sheet, int rowNumber) {
        Row row = sheet.getRow(rowNumber);
        return row == null ? sheet.createRow(rowNumber) : row;
    }

    private String discountDescription(MeasurementSpreadsheetData data) {
        if (data.discount() == null) {
            return "Sem desconto";
        }
        String suffix = data.discount().getDiscountType() == MeasurementDiscountType.PERCENTAGE ? "%" : " R$";
        return data.discount().getDiscountValue().stripTrailingZeros().toPlainString() + suffix;
    }

    private String chargeTypeLabel(MeasurementChargeType chargeType) {
        return switch (chargeType) {
            case SQUARE_METER -> "m²";
            case LINEAR_METER -> "Metro linear";
            case KILOGRAM_PER_SQUARE_METER -> "kg/m²";
            case KILOGRAM_PER_LINEAR_METER -> "kg/metro linear";
        };
    }

    private String display(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String displayId(Object value) {
        return value == null ? "Não vinculado" : value.toString();
    }

    private record Styles(CellStyle title, CellStyle label, CellStyle value, CellStyle date, CellStyle tableHeader,
                          CellStyle text, CellStyle formula, CellStyle quantity, CellStyle currency, CellStyle integer,
                          CellStyle total, CellStyle totalCurrency, CellStyle netTotal, CellStyle netTotalCurrency) {
        static Styles create(XSSFWorkbook workbook) {
            CellStyle title = style(workbook, IndexedColors.DARK_TEAL, IndexedColors.WHITE, true, HorizontalAlignment.LEFT);
            CellStyle label = style(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE, IndexedColors.BLACK, true, HorizontalAlignment.LEFT);
            CellStyle value = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false, HorizontalAlignment.LEFT);
            CellStyle date = copy(workbook, value, "yyyy-mm-dd");
            CellStyle tableHeader = style(workbook, IndexedColors.TEAL, IndexedColors.WHITE, true, HorizontalAlignment.CENTER);
            CellStyle text = style(workbook, IndexedColors.WHITE, IndexedColors.BLACK, false, HorizontalAlignment.LEFT);
            text.setWrapText(true);
            CellStyle formula = copy(workbook, text, null);
            formula.setWrapText(true);
            CellStyle quantity = copy(workbook, text, QUANTITY_FORMAT);
            quantity.setAlignment(HorizontalAlignment.RIGHT);
            CellStyle currency = copy(workbook, text, CURRENCY_FORMAT);
            currency.setAlignment(HorizontalAlignment.RIGHT);
            CellStyle integer = copy(workbook, text, "0");
            integer.setAlignment(HorizontalAlignment.CENTER);
            CellStyle total = style(workbook, IndexedColors.GREY_25_PERCENT, IndexedColors.BLACK, true, HorizontalAlignment.RIGHT);
            CellStyle totalCurrency = copy(workbook, total, CURRENCY_FORMAT);
            CellStyle netTotal = style(workbook, IndexedColors.DARK_TEAL, IndexedColors.WHITE, true, HorizontalAlignment.RIGHT);
            CellStyle netTotalCurrency = copy(workbook, netTotal, CURRENCY_FORMAT);
            return new Styles(title, label, value, date, tableHeader, text, formula, quantity, currency, integer,
                    total, totalCurrency, netTotal, netTotalCurrency);
        }

        private static CellStyle style(XSSFWorkbook workbook, IndexedColors fill, IndexedColors fontColor,
                                       boolean bold, HorizontalAlignment alignment) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(fill.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(alignment);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            var font = workbook.createFont();
            font.setBold(bold);
            font.setColor(fontColor.getIndex());
            style.setFont(font);
            return style;
        }

        private static CellStyle copy(XSSFWorkbook workbook, CellStyle source, String numberFormat) {
            CellStyle copy = workbook.createCellStyle();
            copy.cloneStyleFrom(source);
            if (numberFormat != null) {
                copy.setDataFormat(workbook.createDataFormat().getFormat(numberFormat));
            }
            return copy;
        }
    }
}
