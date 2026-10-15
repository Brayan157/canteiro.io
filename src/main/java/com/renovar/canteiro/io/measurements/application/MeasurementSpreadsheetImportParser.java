package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.measurements.domain.MeasurementChargeType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Reads only editable columns from the official XLSX shape. Totals and formulas
 * are deliberately ignored: calculations remain authoritative on the server.
 */
@Component
public class MeasurementSpreadsheetImportParser {

    private static final int MAX_ROWS = 10_000;
    private static final String ITEM_ID = "Item ID";
    private static final String SERVICE = "Serviço";
    private static final String ACTIVITY = "Atividade";
    private static final String DESCRIPTION = "Descrição";
    private static final String DATE = "Data";
    private static final String TYPE = "Tipo";
    private static final String AREA = "Área (m²)";
    private static final String LINEAR = "Metragem (m)";
    private static final String KG_PER_AREA = "Kg/m²";
    private static final String KG_PER_LINEAR = "Kg/m";
    private static final String UNIT_QUANTITY = "Peças/unid. executadas";
    private static final String UNIT_PRICE = "Preço unit. (R$)";
    private static final String SERVICE_GROUP_PREFIX = "SERVIÇO:";

    public ParsedMeasurementSpreadsheet parse(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Spreadsheet file is required");
        }
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets()>0 && workbook.getSheetAt(0).getSheetName().startsWith("Acumulado")) {
                throw new IllegalArgumentException("The accumulated report is read-only; import the current DRAFT version spreadsheet instead");
            }
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Spreadsheet must contain the Medição sheet");
            }
            Sheet sheet = workbook.getSheet("Medição");
            if (sheet == null) {
                throw new IllegalArgumentException("Spreadsheet must contain a sheet named Medição");
            }
            Map<String, Integer> columns = findColumns(sheet);
            List<MeasurementSpreadsheetImportRow> rows = new ArrayList<>();
            List<MeasurementSpreadsheetImportLineError> errors = new ArrayList<>();
            int headerRow = findHeaderRow(sheet);
            int rowCount = 0;
            for (int index = headerRow + 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (isServiceGroupRow(row)) {
                    continue;
                }
                if (isEmptyEditableRow(row, columns)) {
                    continue;
                }
                if (++rowCount > MAX_ROWS) {
                    errors.add(new MeasurementSpreadsheetImportLineError(index + 1, "row", "Spreadsheet exceeds 10000 item rows"));
                    break;
                }
                try {
                    rows.add(readRow(row, index + 1, columns));
                } catch (IllegalArgumentException ex) {
                    errors.add(new MeasurementSpreadsheetImportLineError(index + 1, field(ex), ex.getMessage()));
                }
            }
            Sheet metadata = workbook.getSheet("_measurement_metadata");
            SpreadsheetIdentity identity = metadata == null ? null : identity(metadata);
            return new ParsedMeasurementSpreadsheet(rows, errors, identity);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid XLSX spreadsheet", ex);
        }
    }

    private Map<String, Integer> findColumns(Sheet sheet) {
        int headerRow = findHeaderRow(sheet);
        Row row = sheet.getRow(headerRow);
        Map<String, Integer> columns = new HashMap<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Cell cell : row) {
            String value = formatter.formatCellValue(cell).trim();
            if (!value.isEmpty()) {
                columns.put(value, cell.getColumnIndex());
            }
        }
        if (columns.containsKey("Preço unit. (decimal)")) columns.put(UNIT_PRICE, columns.get("Preço unit. (decimal)"));
        for (String required : List.of(ITEM_ID, SERVICE, ACTIVITY, DATE, TYPE, AREA, LINEAR, KG_PER_AREA,
                KG_PER_LINEAR, UNIT_QUANTITY, UNIT_PRICE)) {
            if (!columns.containsKey(required)) {
                throw new IllegalArgumentException("Spreadsheet template is missing column: " + required);
            }
        }
        return columns;
    }

    private int findHeaderRow(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (int index = sheet.getFirstRowNum(); index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            for (Cell cell : row) {
                if (ITEM_ID.equals(formatter.formatCellValue(cell).trim())) {
                    return index;
                }
            }
        }
        throw new IllegalArgumentException("Spreadsheet template is missing the stable Item ID column");
    }

    private MeasurementSpreadsheetImportRow readRow(Row row, int spreadsheetRow, Map<String, Integer> columns) {
        if (columns.containsKey("Atividade / descrição")) return readPresentationRow(row, spreadsheetRow, columns);
        UUID itemId = uuid(value(row, columns, ITEM_ID), ITEM_ID);
        String serviceTitle = required(value(row, columns, SERVICE), SERVICE);
        String activity = required(value(row, columns, ACTIVITY), ACTIVITY);
        String description = emptyToNull(value(row, columns, DESCRIPTION));
        LocalDate measuredOn = date(row, columns.get(DATE));
        MeasurementChargeType chargeType = chargeType(value(row, columns, TYPE));
        return new MeasurementSpreadsheetImportRow(spreadsheetRow, itemId, serviceTitle, activity, description,
                measuredOn, chargeType, decimal(row, columns.get(AREA), AREA),
                decimal(row, columns.get(LINEAR), LINEAR), decimal(row, columns.get(KG_PER_AREA), KG_PER_AREA),
                decimal(row, columns.get(KG_PER_LINEAR), KG_PER_LINEAR),
                decimal(row, columns.get(UNIT_QUANTITY), UNIT_QUANTITY), decimal(row, columns.get(UNIT_PRICE), UNIT_PRICE));
    }

    private MeasurementSpreadsheetImportRow readPresentationRow(Row row, int spreadsheetRow, Map<String, Integer> columns) {
        UUID itemId = uuid(value(row, columns, ITEM_ID), ITEM_ID);
        String service = required(value(row, columns, SERVICE), SERVICE);
        String originalActivity = value(row, columns, ACTIVITY);
        String originalDescription = value(row, columns, DESCRIPTION);
        if (originalDescription.equals("-")) originalDescription = "";
        String original = originalActivity + (originalDescription.isEmpty() ? "" : "\n" + originalDescription);
        String presented = value(row.getCell(2));
        String combined = presented.equals(original.substring(0, Math.min(1200, original.length())).trim()) ? original : presented;
        String[] description = combined.split("\n", 2);
        MeasurementChargeType type = chargeType(value(row, columns, TYPE));
        boolean area = type == MeasurementChargeType.SQUARE_METER || type == MeasurementChargeType.KILOGRAM_PER_SQUARE_METER;
        boolean linear = type == MeasurementChargeType.LINEAR_METER || type == MeasurementChargeType.KILOGRAM_PER_LINEAR_METER;
        return new MeasurementSpreadsheetImportRow(spreadsheetRow, itemId, service, required(description[0], ACTIVITY),
                description.length > 1 ? emptyToNull(description[1]) : null, presentationDate(row), type,
                area ? presentedDecimal(row.getCell(5), AREA) : null,
                linear ? presentedDecimal(row.getCell(5), LINEAR) : null,
                type == MeasurementChargeType.KILOGRAM_PER_SQUARE_METER ? presentedDecimal(row.getCell(6), KG_PER_AREA) : null,
                type == MeasurementChargeType.KILOGRAM_PER_LINEAR_METER ? presentedDecimal(row.getCell(6), KG_PER_LINEAR) : null,
                presentedDecimal(row.getCell(4), UNIT_QUANTITY), presentedDecimal(row.getCell(8), UNIT_PRICE));
    }

    private LocalDate presentationDate(Row row) {
        Cell cell = row.getCell(3);
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) return date(row, 3);
        try { return LocalDate.parse(value(cell), java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT)); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(DATE + ": must use DD/MM/YYYY"); }
    }

    private BigDecimal presentedDecimal(Cell cell, String field) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC) return rawNumeric(cell);
        String text = value(cell).trim();
        if (text.isEmpty() || text.equals("-")) return null;
        // Only the explicit report presentation grammar is accepted; never parse via double.
        text = text.replaceFirst("^(R\\$|US\\$|€)\\s*", "").replaceFirst("\\s+(kg/m²|kg/m|m²|m)$", "").trim();
        if (!text.matches("-?(?:[0-9]{1,3}(?:\\.[0-9]{3})*|[0-9]+)(?:,[0-9]{1,4})?"))
            throw new IllegalArgumentException(field + ": invalid report decimal");
        return new BigDecimal(text.replace(".", "").replace(',', '.'));
    }

    private BigDecimal rawNumeric(Cell cell) {
        if (cell instanceof org.apache.poi.xssf.usermodel.XSSFCell xssf) return new BigDecimal(xssf.getRawValue());
        throw new IllegalArgumentException("decimal: expected an XLSX numeric or canonical text cell");
    }

    private SpreadsheetIdentity identity(Sheet metadata) {
        try {
            if (!"measurement-xlsx-v2".equals(value(metadata.getRow(0).getCell(0))))
                throw new IllegalArgumentException("Unsupported spreadsheet metadata version");
            UUID measurementId = uuid(value(metadata.getRow(1).getCell(0)), "measurementId");
            UUID versionId = uuid(value(metadata.getRow(2).getCell(0)), "versionId");
            if (measurementId == null || versionId == null) throw new IllegalArgumentException("Missing spreadsheet origin");
            return new SpreadsheetIdentity(measurementId, versionId,
                    com.renovar.canteiro.io.shared.domain.CurrencyCode.valueOf(value(metadata.getRow(3).getCell(0))));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid spreadsheet origin/currency metadata", ex);
        }
    }

    private boolean isEmptyEditableRow(Row row, Map<String, Integer> columns) {
        if (row == null) return true;
        return List.of(ITEM_ID, SERVICE, ACTIVITY, TYPE, AREA, LINEAR, KG_PER_AREA, KG_PER_LINEAR, UNIT_QUANTITY, UNIT_PRICE)
                .stream().allMatch(header -> value(row, columns, header).isBlank());
    }

    /**
     * Group titles are presentation-only rows emitted by the official exporter.
     * They intentionally sit in the first, merged column so the stable Serviço
     * column of every actual item remains available for re-import.
     */
    private boolean isServiceGroupRow(Row row) {
        return row != null && value(row.getCell(0)).startsWith(SERVICE_GROUP_PREFIX);
    }

    private String value(Row row, Map<String, Integer> columns, String header) {
        Integer column = columns.get(header);
        return column == null ? "" : value(row.getCell(column));
    }

    private String value(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException("formula: Editable spreadsheet cells must not contain formulas");
        }
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }

    private UUID uuid(String value, String field) {
        if (value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + ": must be a UUID from the exported spreadsheet");
        }
    }

    private LocalDate date(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new IllegalArgumentException(DATE + ": is required");
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String text = value(cell);
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(DATE + ": must use YYYY-MM-DD");
        }
    }

    private BigDecimal decimal(Row row, int column, String field) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC) return rawNumeric(cell);
        String text = value(cell).replace(',', '.');
        if (text.isBlank()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + ": must be a decimal number");
        }
    }

    private MeasurementChargeType chargeType(String value) {
        return switch (value) {
            case "m²", "SQUARE_METER" -> MeasurementChargeType.SQUARE_METER;
            case "Metro linear", "LINEAR_METER" -> MeasurementChargeType.LINEAR_METER;
            case "kg/m²", "KILOGRAM_PER_SQUARE_METER" -> MeasurementChargeType.KILOGRAM_PER_SQUARE_METER;
            case "kg/metro linear", "KILOGRAM_PER_LINEAR_METER" -> MeasurementChargeType.KILOGRAM_PER_LINEAR_METER;
            case "Unidade", "UNIT" -> MeasurementChargeType.UNIT;
            default -> throw new IllegalArgumentException(TYPE + ": unsupported measurement charge type");
        };
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + ": is required");
        return value.trim();
    }

    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String field(IllegalArgumentException ex) {
        String message = ex.getMessage() == null ? "row" : ex.getMessage();
        int separator = message.indexOf(':');
        return separator < 0 ? "row" : message.substring(0, separator);
    }

    public record SpreadsheetIdentity(UUID measurementId, UUID versionId, com.renovar.canteiro.io.shared.domain.CurrencyCode currency) { }
    public record ParsedMeasurementSpreadsheet(List<MeasurementSpreadsheetImportRow> rows,
                                               List<MeasurementSpreadsheetImportLineError> lineErrors,
                                               SpreadsheetIdentity identity) {
    }
}
