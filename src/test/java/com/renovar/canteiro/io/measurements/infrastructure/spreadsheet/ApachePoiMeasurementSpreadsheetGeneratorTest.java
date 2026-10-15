package com.renovar.canteiro.io.measurements.infrastructure.spreadsheet;

import com.renovar.canteiro.io.measurements.application.MeasurementExportData;
import com.renovar.canteiro.io.measurements.application.MeasurementCompanyBranding;
import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetImportParser;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.CellType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApachePoiMeasurementSpreadsheetGeneratorTest {
    @Test void preservesExactDecimalsAndStableImportIdentityBehindTenVisibleColumnsInAllCurrencies() throws Exception {
        for (CurrencyCode currency : CurrencyCode.values()) {
            UUID company=UUID.randomUUID(),mid=UUID.randomUUID(),vid=UUID.randomUUID();
            var m=Measurement.rehydrate(mid,company,null,null,"Medição da cobertura","AGO/2026",null,LocalDate.of(2026,8,19),MeasurementStatus.DRAFT,0,Instant.now(),Instant.now(),currency);
            var v=MeasurementVersion.rehydrate(vid,company,mid,1,null,MeasurementVersionStatus.DRAFT,0,null,null,Instant.now(),Instant.now());
            var area=MeasurementItem.createSquareMeter(company,vid,1,"Telhado","Área coberta","Descrição completa",LocalDate.of(2026,8,19),new BigDecimal("10.0000"),new BigDecimal("25.00"))
                    .withOperationalUnitQuantity(new BigDecimal("2.0000"));
            var weight=MeasurementItem.createKilogramPerLinearMeter(company,vid,2,"Calha","Montagem",null,LocalDate.of(2026,8,19),new BigDecimal("2.5000"),new BigDecimal("4.0000"),new BigDecimal("8.00"));
            var discount=MeasurementDiscount.create(company,vid,MeasurementDiscountType.PERCENTAGE,new BigDecimal("10.0000"));
            var output=new ByteArrayOutputStream();
            var result=new ApachePoiMeasurementSpreadsheetGenerator().write(new MeasurementExportData(m,v,discount,
                    new MeasurementCompanyBranding("Emitente privado",logo())),consumer->{consumer.accept(area);consumer.accept(weight);},output);
            assertEquals(2,result.itemCount());assertEquals(new BigDecimal("297.00"),result.amounts().netAmount());
            try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
                var sheet=workbook.getSheet("Medição");assertEquals(1,workbook.getAllPictures().size());
                assertEquals("Medição da cobertura",sheet.getRow(1).getCell(0).getStringCellValue());
                assertEquals(HorizontalAlignment.CENTER,sheet.getRow(1).getCell(0).getCellStyle().getAlignment());
                for(int c=0;c<10;c++)assertFalse(sheet.isColumnHidden(c));
                for(int c=10;c<28;c++)assertTrue(sheet.isColumnHidden(c));
                assertEquals("Item ID",sheet.getRow(8).getCell(24).getStringCellValue());
                assertEquals("SERVIÇO: Telhado",sheet.getRow(9).getCell(0).getStringCellValue());
                assertEquals(HorizontalAlignment.CENTER,sheet.getRow(9).getCell(0).getCellStyle().getAlignment());
                assertEquals("Telhado",sheet.getRow(10).getCell(11).getStringCellValue());
                assertEquals("Q11*V11",sheet.getRow(10).getCell(23).getCellFormula());
                assertEquals("25.00",sheet.getRow(10).getCell(21).getStringCellValue());
                assertTrue(workbook.isSheetVeryHidden(1));
                for(var row:sheet)for(var cell:row)if(cell.getColumnIndex()<10&&cell.getCellType()==CellType.STRING){
                    assertFalse(cell.getStringCellValue().contains("KILOGRAM_PER"));assertFalse(cell.getStringCellValue().equals("Fórmula"));
                }
            }
            var imported=new MeasurementSpreadsheetImportParser().parse(output.toByteArray());
            assertEquals(2,imported.rows().size());assertTrue(imported.lineErrors().isEmpty(),imported.lineErrors().toString());
            assertEquals(currency,imported.identity().currency());assertEquals(vid,imported.identity().versionId());
            assertEquals(0,imported.rows().getFirst().unitPrice().compareTo(new BigDecimal("25.00")));
            assertEquals(0,imported.rows().getFirst().unitQuantity().compareTo(new BigDecimal("2.0000")));
            assertEquals("Descrição completa",imported.rows().getFirst().description());
            Files.createDirectories(Path.of("tmp","sheets"));Files.write(Path.of("tmp","sheets","medicao-versao-"+currency+".xlsx"),output.toByteArray());
        }
    }

    @Test void preservesLargeMoneyWithoutBinaryConversionAndUsesFallbackForInvalidLogo() throws Exception {
        UUID company=UUID.randomUUID(),mid=UUID.randomUUID(),vid=UUID.randomUUID();
        var m=Measurement.rehydrate(mid,company,null,null,"Precisão",null,null,LocalDate.now(),MeasurementStatus.DRAFT,0,Instant.now(),Instant.now(),CurrencyCode.USD);
        var v=MeasurementVersion.rehydrate(vid,company,mid,1,null,MeasurementVersionStatus.DRAFT,0,null,null,Instant.now(),Instant.now());
        var item=MeasurementItem.createUnit(company,vid,1,"Serviço","Unidade",null,LocalDate.now(),BigDecimal.ONE,new BigDecimal("9007199254740993.99"));
        for(byte[] logo:List.of(new byte[0],new byte[]{(byte)0x89,0x50,0x4e,0x47,13,10,26,10})) {
            var output=new ByteArrayOutputStream();new ApachePoiMeasurementSpreadsheetGenerator().write(new MeasurementExportData(m,v,null,
                    new MeasurementCompanyBranding("Empresa emitente",logo)),consumer->consumer.accept(item),output);
            var parsed=new MeasurementSpreadsheetImportParser().parse(output.toByteArray());assertTrue(parsed.lineErrors().isEmpty());
            assertEquals(new BigDecimal("9007199254740993.99"),parsed.rows().getFirst().unitPrice());
            try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))){assertEquals(0,workbook.getAllPictures().size());}
        }
    }

    private byte[] logo() throws Exception {
        var image=new BufferedImage(160,40,BufferedImage.TYPE_INT_ARGB);var graphics=image.createGraphics();
        graphics.setColor(new java.awt.Color(0,105,95));graphics.fillRect(0,0,160,40);graphics.dispose();
        var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();
    }
}
