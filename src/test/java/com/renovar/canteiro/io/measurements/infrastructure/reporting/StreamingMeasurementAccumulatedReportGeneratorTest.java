package com.renovar.canteiro.io.measurements.infrastructure.reporting;

import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedLine;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedLineSource;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedReportData;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedServiceSubtotal;
import com.renovar.canteiro.io.measurements.application.MeasurementCompanyBranding;
import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedTotalsResponse;
import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;
import com.renovar.canteiro.io.measurements.domain.MeasurementFinancialSummary;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingMeasurementAccumulatedReportGeneratorTest {
    @Test void rendersMixedTypesWithOperationalUnitsAndOfficialSeparatedTotalsInBrlUsdEur()throws Exception {
        for(var currency:com.renovar.canteiro.io.shared.domain.CurrencyCode.values()) {
            UUID company=UUID.randomUUID(),mid=UUID.randomUUID(),vid=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
            var m=Measurement.rehydrate(mid,company,null,null,"Serviços Fábrica de Açúcar","MED-2026",null,LocalDate.of(2026,10,14),MeasurementStatus.DRAFT,0,Instant.now(),Instant.now(),currency);
            var v=MeasurementVersion.rehydrate(vid,company,mid,2,UUID.randomUUID(),MeasurementVersionStatus.DRAFT,0,null,null,Instant.now(),Instant.now());
            var weighted=MeasurementItem.createKilogramPerLinearMeter(company,vid,1,"Linha de Vinhaça","Montagem de tubulação","Trecho executado e aceito.",LocalDate.of(2026,10,12),new BigDecimal("2.0000"),new BigDecimal("3.0000"),new BigDecimal("25.00"));
            var linear=MeasurementItem.createLinearMeter(company,vid,2,"Linha de Vinhaça","Instalação de curva",null,LocalDate.of(2026,10,12),new BigDecimal("2.0000"),new BigDecimal("20.00")).withOperationalUnitQuantity(new BigDecimal("1.0000"));
            var unit=MeasurementItem.createUnit(company,vid,1,"Linha de Vinhaça","Novas válvulas","Avanço pendente de aceite.",LocalDate.of(2026,10,14),new BigDecimal("2.0000"),new BigDecimal("30.00"));
            var area=MeasurementItem.createSquareMeter(company,vid,3,"Linha de cobre","Revestimento",null,LocalDate.of(2026,10,12),new BigDecimal("4.0000"),new BigDecimal("15.00"));
            var weightArea=MeasurementItem.createKilogramPerSquareMeter(company,vid,4,"Linha de cobre","Chapas de reforço",null,LocalDate.of(2026,10,12),new BigDecimal("3.0000"),new BigDecimal("2.0000"),new BigDecimal("25.00"));
            List<MeasurementAccumulatedLine> lines=List.of(new MeasurementAccumulatedLine(a,"Linha de Vinhaça",weighted,1,"ACCEPTED","Aceito"),
                    new MeasurementAccumulatedLine(a,"Linha de Vinhaça",linear,1,"ACCEPTED","Aceito"),new MeasurementAccumulatedLine(a,"Linha de Vinhaça",unit,2,"DRAFT","Em revisão"),
                    new MeasurementAccumulatedLine(b,"Linha de cobre",area,1,"ACCEPTED","Aceito"),new MeasurementAccumulatedLine(b,"Linha de cobre",weightArea,1,"ACCEPTED","Aceito"));
            MeasurementAccumulatedLineSource source=consumer->{for(var line:lines)consumer.accept(line);};
            var accepted=MeasurementAccumulatedTotalsResponse.of(4,new BigDecimal("400.00"),new BigDecimal("360.00"),new BigDecimal("12.0000"));
            var pending=MeasurementAccumulatedTotalsResponse.of(1,new BigDecimal("60.00"),new BigDecimal("60.00"),null);
            var zero=MeasurementAccumulatedTotalsResponse.of(0,BigDecimal.ZERO,BigDecimal.ZERO,null);
            var finance=new MeasurementFinancialSummary(mid,null,new BigDecimal("360.00"),new BigDecimal("200.00"),new BigDecimal("100.00"),
                    new BigDecimal("160.00"),new BigDecimal("100.00"),List.of(),"AVAILABLE",Instant.parse("2026-10-14T15:00:00Z"),new BigDecimal("260.00"),BigDecimal.ZERO,BigDecimal.ZERO,currency);
            byte[] logo=null;
            if(currency==com.renovar.canteiro.io.shared.domain.CurrencyCode.BRL){var picture=new BufferedImage(160,40,BufferedImage.TYPE_INT_ARGB);
                var g=picture.createGraphics();g.setColor(new Color(0,105,95));g.fillRect(10,5,140,30);g.dispose();var bytes=new ByteArrayOutputStream();ImageIO.write(picture,"png",bytes);logo=bytes.toByteArray();}
            if(currency==com.renovar.canteiro.io.shared.domain.CurrencyCode.EUR)logo=new byte[]{(byte)0x89,0x50,0x4e,0x47,13,10,26,10};
            var data=new MeasurementAccumulatedReportData(m,v,new MeasurementCompanyBranding("Engenharia Exemplo · emitente",logo),accepted,pending,finance,List.of(
                    new MeasurementAccumulatedServiceSubtotal(a,"Linha de Vinhaça",MeasurementAccumulatedTotalsResponse.of(2,new BigDecimal("190.00"),new BigDecimal("171.00"),new BigDecimal("6.0000")),pending),
                    new MeasurementAccumulatedServiceSubtotal(b,"Linha de cobre",MeasurementAccumulatedTotalsResponse.of(2,new BigDecimal("210.00"),new BigDecimal("189.00"),new BigDecimal("6.0000")),zero)));
            var pdf=new ByteArrayOutputStream();var xlsx=new ByteArrayOutputStream();var generator=new StreamingMeasurementAccumulatedReportGenerator();
            assertEquals(5,generator.writePdf(data,source,pdf));assertEquals(5,generator.writeSpreadsheet(data,source,xlsx));
            try(var doc=Loader.loadPDF(pdf.toByteArray())) {
                String text=new PDFTextStripper().getText(doc);String symbol=currency.getSymbol();
                for(String amount:List.of("25,00","150,00","171,00","189,00","40,00","360,00","100,00","260,00"))assertTrue(text.contains(symbol+" "+amount),text);
                assertTrue(text.contains("1.1"));assertTrue(text.contains("2.1"));assertTrue(text.contains("Em revisão"));assertTrue(text.contains("kg/m²"));
                for(var type:com.renovar.canteiro.io.measurements.domain.MeasurementChargeType.values())org.junit.jupiter.api.Assertions.assertFalse(text.contains(type.name()));
                assertEquals(currency==com.renovar.canteiro.io.shared.domain.CurrencyCode.BRL,doc.getPage(0).getResources().getXObjectNames().iterator().hasNext());
            }
            Files.createDirectories(Path.of("output","pdf"));Files.createDirectories(Path.of("tmp","sheets"));
            Files.write(Path.of("output","pdf","medicao-acumulada-"+currency+".pdf"),pdf.toByteArray());
            Files.write(Path.of("tmp","sheets","medicao-acumulada-"+currency+".xlsx"),xlsx.toByteArray());
        }
    }
    @Test void rendersTheFullLongTitleAndFallbackCompanyNameWithoutSharingTheSameHeaderArea() throws Exception {
        UUID company=UUID.randomUUID(),mid=UUID.randomUUID();
        String title=("Medição de tubulação e complementos mensais ".repeat(5)).substring(0,200).trim();
        String companyName=("Empresa prestadora de serviços de construção ".repeat(6)).substring(0,255).trim();
        Measurement m=Measurement.rehydrate(mid,company,null,null,title,null,null,LocalDate.of(2026,10,13),MeasurementStatus.DRAFT,0,Instant.now(),Instant.now());
        var v=MeasurementVersion.create(company,mid,1);
        var f=new MeasurementFinancialSummary(mid,null,new BigDecimal("0.00"),null,null,null,null,List.of(),"NO_CONTRACT",Instant.parse("2026-10-13T15:00:00Z"),null,null,null);
        var zero=MeasurementAccumulatedTotalsResponse.of(0,new BigDecimal("0.00"),new BigDecimal("0.00"),null);
        var data=new MeasurementAccumulatedReportData(m,v,new MeasurementCompanyBranding(companyName,null),zero,zero,f);
        var output=new ByteArrayOutputStream();new StreamingMeasurementAccumulatedReportGenerator().writePdf(data,consumer->{},output);
        try(var doc=Loader.loadPDF(output.toByteArray())) {
            String text=new PDFTextStripper().getText(doc).replaceAll("\\s+"," ");
            assertTrue(text.contains(title));assertTrue(text.contains(companyName));
        }
        Files.createDirectories(Path.of("tmp","pdfs"));Files.write(Path.of("tmp","pdfs","acumulado-titulo-longo.pdf"),output.toByteArray());
    }
    @Test void streamsAllRowsAcrossPagesWithPrivateLogoAndMultipleServices() throws Exception {
        UUID company=UUID.randomUUID(),mid=UUID.randomUUID(),vid=UUID.randomUUID(),sid=UUID.randomUUID();
        Measurement m=Measurement.rehydrate(mid,company,null,null,"Relatório acumulado", "MED-01",null,LocalDate.of(2026,10,13),MeasurementStatus.DRAFT,0,Instant.now(),Instant.now());
        MeasurementVersion v=MeasurementVersion.rehydrate(vid,company,mid,2,UUID.randomUUID(),MeasurementVersionStatus.DRAFT,0,null,null,Instant.now(),Instant.now());
        BufferedImage logo=new BufferedImage(100,40,BufferedImage.TYPE_INT_RGB);var graphics=logo.createGraphics();graphics.setColor(new Color(0,105,95));graphics.fillRect(0,0,100,40);graphics.dispose();
        ByteArrayOutputStream picture=new ByteArrayOutputStream();ImageIO.write(logo,"png",picture);
        var finance=new MeasurementFinancialSummary(mid,null,new BigDecimal("750.00"),null,null,null,null,List.of(),"NO_CONTRACT",Instant.parse("2026-10-13T15:00:00Z"),null,null,null);
        var data=new MeasurementAccumulatedReportData(m,v,new MeasurementCompanyBranding("Emitente de teste",picture.toByteArray()),
            MeasurementAccumulatedTotalsResponse.of(30,new BigDecimal("750.00"),new BigDecimal("750.00"),null),MeasurementAccumulatedTotalsResponse.of(1,new BigDecimal("25.00"),new BigDecimal("25.00"),null),finance,
            List.of(new MeasurementAccumulatedServiceSubtotal(sid,"Linha A",MeasurementAccumulatedTotalsResponse.of(15,new BigDecimal("375.00"),new BigDecimal("375.00"),null),
                MeasurementAccumulatedTotalsResponse.of(0,BigDecimal.ZERO,BigDecimal.ZERO,null)),
                new MeasurementAccumulatedServiceSubtotal(mid,"Linha B",MeasurementAccumulatedTotalsResponse.of(15,new BigDecimal("375.00"),new BigDecimal("375.00"),null),
                    MeasurementAccumulatedTotalsResponse.of(1,new BigDecimal("25.00"),new BigDecimal("25.00"),null))));
        MeasurementAccumulatedLineSource source=consumer->{
            for(int n=1;n<=31;n++) {
                var raw=MeasurementItem.createLinearMeter(company,vid,n,"Linha "+(n<=15?"A":"B"),"Atividade "+n,"Descrição longa para conferência do relatório. ".repeat(8),LocalDate.of(2026,10,13),BigDecimal.ONE,new BigDecimal("25.00"));
                var item=MeasurementItem.rehydrate(UUID.randomUUID(),company,vid,n,raw.getServiceTitle(),raw.getActivity(),raw.getDescription(),raw.getMeasuredOn(),raw.getChargeType(),
                    raw.getAreaSquareMeters(),raw.getLinearMeters(),raw.getKilogramsPerSquareMeter(),raw.getKilogramsPerLinearMeter(),raw.getUnitQuantity(),raw.getUnitPrice(),raw.getTotalWeightKg(),raw.getTotalAmount(),raw.getCalculationFormula(),true,null,Instant.now(),Instant.now());
                consumer.accept(new MeasurementAccumulatedLine(n<=15?sid:mid,raw.getServiceTitle(),item,n==31?2:1,n==31?"DRAFT":"ACCEPTED",n==31?"Em revisão / aguardando aceite":"Aceito"));
            }
        };
        var generator=new StreamingMeasurementAccumulatedReportGenerator();var pdf=new ByteArrayOutputStream();var xlsx=new ByteArrayOutputStream();
        assertEquals(31,generator.writePdf(data,source,pdf));assertEquals(31,generator.writeSpreadsheet(data,source,xlsx));
        try(var document=Loader.loadPDF(pdf.toByteArray())) {
            assertTrue(document.getNumberOfPages()>1);String text=new PDFTextStripper().getText(document);
            assertTrue(text.contains("Atividade 31"));assertTrue(text.contains("SERVIÇO: Linha A"));assertTrue(text.contains("SERVIÇO: Linha B"));assertTrue(text.contains("R$ 750,00"));
            assertEquals(2,text.split("Subtotal líquido aceito",-1).length-1);
            assertTrue(document.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
        try(var workbook=WorkbookFactory.create(new ByteArrayInputStream(xlsx.toByteArray()))) {
            assertEquals(1,workbook.getAllPictures().size());assertTrue(workbook.getSheetAt(0).isColumnHidden(16));
        }
        Files.createDirectories(Path.of("tmp","pdfs"));Files.write(Path.of("tmp","pdfs","acumulado-multipagina.pdf"),pdf.toByteArray());
    }
}
