package com.renovar.canteiro.io.measurements.infrastructure.spreadsheet;

import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetData;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApachePoiMeasurementSpreadsheetGeneratorTest {

    private final ApachePoiMeasurementSpreadsheetGenerator generator = new ApachePoiMeasurementSpreadsheetGenerator();

    @Test
    void generatesAuditableXlsxWithItemAndTotalFormulas() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID measurementId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Measurement measurement = Measurement.create(companyId, UUID.randomUUID(), null, "Ago/2026", "Cobertura",
                LocalDate.of(2026, 8, 19));
        MeasurementVersion version = MeasurementVersion.create(companyId, measurementId, 1);
        MeasurementItem areaItem = MeasurementItem.createSquareMeter(companyId, versionId, 1, "Telhado", "Área coberta",
                new BigDecimal("10.0000"), new BigDecimal("25.00"));
        MeasurementItem weightItem = MeasurementItem.createKilogramPerLinearMeter(companyId, versionId, 2, "Calha", null,
                new BigDecimal("2.5000"), new BigDecimal("4.0000"), new BigDecimal("8.00"));
        MeasurementDiscount discount = MeasurementDiscount.create(companyId, versionId, MeasurementDiscountType.PERCENTAGE,
                new BigDecimal("10.0000"));
        var amounts = MeasurementVersionAmountCalculator.calculate(List.of(areaItem, weightItem), discount);

        byte[] result = generator.generate(new MeasurementSpreadsheetData(measurement, version,
                List.of(areaItem, weightItem), discount, amounts));

        assertTrue(result.length > 4);
        assertEquals((byte) 'P', result[0]);
        assertEquals((byte) 'K', result[1]);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(result))) {
            var sheet = workbook.getSheet("Medição");
            assertEquals("Medição Ago/2026", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("E10*I10", sheet.getRow(9).getCell(10).getCellFormula());
            assertEquals("F11*H11", sheet.getRow(10).getCell(9).getCellFormula());
            assertEquals("J11*I11", sheet.getRow(10).getCell(10).getCellFormula());
            assertEquals("SUM(K10:K11)", sheet.getRow(12).getCell(10).getCellFormula());
            assertEquals("K13*10.0000/100", sheet.getRow(13).getCell(10).getCellFormula());
            assertEquals("K13-K14", sheet.getRow(14).getCell(10).getCellFormula());
        }
    }
}
