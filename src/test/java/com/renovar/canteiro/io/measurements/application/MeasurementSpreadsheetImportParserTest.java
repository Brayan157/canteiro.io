package com.renovar.canteiro.io.measurements.application;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementSpreadsheetImportParserTest {

    private final MeasurementSpreadsheetImportParser parser = new MeasurementSpreadsheetImportParser();

    @Test
    void readsTheStableItemIdInsteadOfTheVisualItemNumberOrTitles() throws Exception {
        UUID itemId = UUID.randomUUID();
        var parsed = parser.parse(workbook(itemId, "42", "Linha alterada", "Atividade alterada"));

        assertFalse(parsed.rows().isEmpty());
        assertEquals(itemId, parsed.rows().getFirst().itemId());
        assertEquals("Linha alterada", parsed.rows().getFirst().serviceTitle());
        assertEquals("Atividade alterada", parsed.rows().getFirst().activity());
        assertEquals("LINEAR_METER", parsed.rows().getFirst().chargeType().name());
    }

    @Test
    void refusesFilesWithoutTheStableItemIdTemplateColumn() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Medição");
            sheet.createRow(0).createCell(0).setCellValue("Nº");
            workbook.write(output);
            assertThrows(IllegalArgumentException.class, () -> parser.parse(output.toByteArray()));
        }
    }

    @Test
    void ignoresPresentationOnlyServiceGroupRows() throws Exception {
        UUID itemId = UUID.randomUUID();
        var parsed = parser.parse(workbookWithServiceGroup(itemId));

        assertEquals(1, parsed.rows().size());
        assertEquals(itemId, parsed.rows().getFirst().itemId());
        assertTrue(parsed.lineErrors().isEmpty());
    }

    private byte[] workbook(UUID itemId, String number, String service, String activity) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Medição");
            String[] headers = {"Nº", "Serviço", "Atividade", "Descrição", "Data", "Tipo", "Área (m²)",
                    "Metragem (m)", "Kg/m²", "Kg/m", "Peças/unid. executadas", "Preço unit. (R$)", "Item ID"};
            var header = sheet.createRow(8);
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(9);
            row.createCell(0).setCellValue(number);
            row.createCell(1).setCellValue(service);
            row.createCell(2).setCellValue(activity);
            row.createCell(4).setCellValue("2026-10-06");
            row.createCell(5).setCellValue("Metro linear");
            row.createCell(7).setCellValue(12);
            row.createCell(11).setCellValue(25);
            row.createCell(12).setCellValue(itemId.toString());
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithServiceGroup(UUID itemId) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Medição");
            String[] headers = {"Nº", "Serviço", "Atividade", "Descrição", "Data", "Tipo", "Área (m²)",
                    "Metragem (m)", "Kg/m²", "Kg/m", "Peças/unid. executadas", "Preço unit. (R$)", "Item ID"};
            var header = sheet.createRow(8);
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            sheet.createRow(9).createCell(0).setCellValue("SERVIÇO: Linha de vinhaça");
            var row = sheet.createRow(10);
            row.createCell(0).setCellValue("1");
            row.createCell(1).setCellValue("Linha de vinhaça");
            row.createCell(2).setCellValue("Assentamento");
            row.createCell(4).setCellValue("2026-10-06");
            row.createCell(5).setCellValue("Metro linear");
            row.createCell(7).setCellValue(12);
            row.createCell(11).setCellValue(25);
            row.createCell(12).setCellValue(itemId.toString());
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
