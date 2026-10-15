package com.renovar.canteiro.io.measurements.infrastructure.reporting;

import com.renovar.canteiro.io.measurements.application.MeasurementExportData;
import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetImportParser;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.measurements.infrastructure.pdf.PdfBoxMeasurementPdfGenerator;
import com.renovar.canteiro.io.measurements.infrastructure.spreadsheet.ApachePoiMeasurementSpreadsheetGenerator;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementReportIdentityTest {
    @Test void neverMergesDifferentContinuousIdentitiesJustBecauseTheirTitlesMatch()throws Exception {
        UUID company=UUID.randomUUID(),mid=UUID.randomUUID(),vid=UUID.randomUUID();
        var m=Measurement.rehydrate(mid,company,null,null,"Serviços independentes",null,null,LocalDate.now(),MeasurementStatus.DRAFT,0,Instant.now(),Instant.now(),CurrencyCode.USD);
        var v=MeasurementVersion.rehydrate(vid,company,mid,1,null,MeasurementVersionStatus.DRAFT,0,null,null,Instant.now(),Instant.now());
        var one=persisted(MeasurementItem.createLinearMeter(company,vid,1,"Mesmo título","Primeiro",null,LocalDate.now(),BigDecimal.ONE,new BigDecimal("10.00")));
        var two=persisted(MeasurementItem.createLinearMeter(company,vid,2,"Mesmo título","Segundo",null,LocalDate.now(),BigDecimal.ONE,new BigDecimal("20.00")));
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();Map<UUID,UUID> bindings=Map.of(one.getId(),a,two.getId(),b);
        var data=new MeasurementExportData(m,v,null,null,bindings::get);
        var pdf=new ByteArrayOutputStream();var xlsx=new ByteArrayOutputStream();
        new PdfBoxMeasurementPdfGenerator().write(data,consumer->{consumer.accept(one);consumer.accept(two);},pdf);
        new ApachePoiMeasurementSpreadsheetGenerator().write(data,consumer->{consumer.accept(one);consumer.accept(two);},xlsx);
        try(var document=Loader.loadPDF(pdf.toByteArray())){
            String text=new PDFTextStripper().getText(document);assertEquals(2,text.split("SERVIÇO: Mesmo título",-1).length-1);
            assertEquals(2,text.split("Subtotal bruto do serviço",-1).length-1);assertTrue(text.contains("US$ 30,00"));
        }
        byte[] spreadsheet=xlsx.toByteArray();
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(spreadsheet))){
            int groups=0;var identities=new java.util.HashSet<String>();
            for(var row:workbook.getSheetAt(0)){
                if(row.getCell(0)!=null&&row.getCell(0).toString().startsWith("SERVIÇO:"))groups++;
                if(row.getCell(28)!=null&&row.getCell(24)!=null&&!row.getCell(24).toString().equals("Item ID"))identities.add(row.getCell(28).toString());
            }
            assertEquals(2,groups);assertEquals(java.util.Set.of(a.toString(),b.toString()),identities);
        }
        var parsed=new MeasurementSpreadsheetImportParser().parse(xlsx.toByteArray());assertTrue(parsed.lineErrors().isEmpty());
        assertEquals(List.of(one.getId(),two.getId()),parsed.rows().stream().map(r->r.itemId()).toList());
    }

    @Test void formatsDecimalMoneyDirectlyWithoutLosingCentsAboveBinaryIntegerPrecision(){
        assertEquals("R$ 8.000,00",MeasurementReportPresentation.money(new BigDecimal("8000.00"),CurrencyCode.BRL));
        assertEquals("US$ 9.007.199.254.740.993,99",MeasurementReportPresentation.money(new BigDecimal("9007199254740993.99"),CurrencyCode.USD));
        assertEquals("€ 8.000,00",MeasurementReportPresentation.money(new BigDecimal("8000.00"),CurrencyCode.EUR));
    }

    private MeasurementItem persisted(MeasurementItem item){
        return MeasurementItem.rehydrate(UUID.randomUUID(),item.getCompanyId(),item.getMeasurementVersionId(),item.getItemNumber(),item.getServiceTitle(),item.getActivity(),item.getDescription(),
                item.getMeasuredOn(),item.getChargeType(),item.getAreaSquareMeters(),item.getLinearMeters(),item.getKilogramsPerSquareMeter(),item.getKilogramsPerLinearMeter(),
                item.getUnitQuantity(),item.getUnitPrice(),item.getTotalWeightKg(),item.getTotalAmount(),item.getCalculationFormula(),true,null,Instant.now(),Instant.now());
    }
}
