package com.renovar.canteiro.io.receivables;

import com.renovar.canteiro.io.contracts.domain.ContractNetAmount;
import com.renovar.canteiro.io.receivables.application.ContractBillingPdfGenerator;
import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository;
import com.renovar.canteiro.io.receivables.infrastructure.reporting.PdfBoxContractBillingPdfGenerator;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxContractBillingPdfGeneratorTest {
    @Test void coversAllTwentyOneInvoicesAndMoreThanOneServiceBatchWithoutDuplicatingFinalTotals() throws Exception {
        var source=new Source(121,21);var output=new ByteArrayOutputStream();
        var result=new PdfBoxContractBillingPdfGenerator().write(data(null,121,21),source,output);
        assertEquals(121,result.serviceRows());assertEquals(21,result.invoiceRows());assertTrue(result.pages()>14);
        assertEquals(100,source.maxServiceBatch);assertTrue(source.maxCells<=303);
        try(var pdf=Loader.loadPDF(output.toByteArray())) {
            String text=new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("NF-SINTETICA-21"));assertTrue(text.contains("Serviço sintético 121"));
            assertEquals(1,text.split("RESUMO DO CONTRATO",-1).length-1);
            assertTrue(text.contains("12.029,00"));
        }
    }
    @Test void rendersPrivateLogoForVisualReview() throws Exception {
        var image=new java.awt.image.BufferedImage(120,42,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();graphics.setColor(new java.awt.Color(24,72,110));graphics.fillRect(0,0,120,42);
        graphics.setColor(java.awt.Color.WHITE);graphics.drawString("DEMONSTRACAO",10,25);graphics.dispose();
        var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        var output=new ByteArrayOutputStream();new PdfBoxContractBillingPdfGenerator().write(data(bytes.toByteArray(),2,2),new Source(2,2),output);
        try(var pdf=Loader.loadPDF(output.toByteArray())) {
            assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
            assertTrue(new PDFTextStripper().getText(pdf).contains("NF-SINTETICA-2"));
        }
        save("billing-control-synthetic-logo.pdf",output.toByteArray());
    }
    @Test void fallsBackForUnsupportedOrMalformedWebpWithoutPublicFetching() throws Exception {
        var output=new ByteArrayOutputStream();new PdfBoxContractBillingPdfGenerator().write(data(new byte[]{'R','I','F','F',0,0,0,0,'W','E','B','P'},2,2),new Source(2,2),output);
        try(var pdf=Loader.loadPDF(output.toByteArray())) {
            assertFalse(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
            assertTrue(new PDFTextStripper().getText(pdf).contains("Emitente de demonstração"));
        }
        save("billing-control-synthetic-fallback.pdf",output.toByteArray());
    }
    @Test void emitsAnEmptyContractWithoutInventingInvoicesOrBalances() throws Exception {
        var empty=new Source(0,0);var output=new ByteArrayOutputStream();
        var data=new ContractBillingPdfGenerator.Data(UUID.randomUUID(),UUID.randomUUID(),"Emitente sem logo",null,null,"Cliente sintético","Obra sintética","Sem serviços","ACTIVE",CurrencyCode.USD,Instant.parse("2026-10-29T12:00:00Z"),new ContractNetAmount(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO),BigDecimal.ZERO,BigDecimal.ZERO,null,"DEMO/2026");
        var result=new PdfBoxContractBillingPdfGenerator().write(data,empty,output);
        assertEquals(0,result.invoiceRows());assertEquals(0,result.serviceRows());
        try(var pdf=Loader.loadPDF(output.toByteArray())) {String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("Moeda: USD"));assertTrue(text.contains("Nenhuma nota fiscal"));assertTrue(text.contains("Saldo total"));}
    }
    @Test void explanatoryNotesStayBelowTheTableTotals() throws Exception {
        var output=new ByteArrayOutputStream();new PdfBoxContractBillingPdfGenerator().write(data(null,2,3),new Source(2,3),output);
        try(var pdf=Loader.loadPDF(output.toByteArray())) {
            var positions=new PDFTextStripper(){
                @Override protected void writeString(String text,List<org.apache.pdfbox.text.TextPosition> parts)throws IOException {
                    if(getCurrentPageNo()==1 && text.contains("Totais: bloco")) totalY=parts.getFirst().getYDirAdj();
                    if(getCurrentPageNo()==1 && text.contains("Parcelas representam")) noteY=parts.getFirst().getYDirAdj();
                    super.writeString(text,parts);
                }
                float totalY=-1,noteY=-1;
            };
            String text=positions.getText(pdf);assertTrue(positions.totalY>0);assertTrue(positions.noteY-positions.totalY>=20);
            assertFalse(text.contains("NFs nenhuma"));
        }
    }
    @Test void keepsMaximumPrecisionMoneyOnOneLineWithoutRoundingOrBinaryConversion() throws Exception {
        var source=new Source(1,1);var amount=new BigDecimal("99999999999999999.99");
        var row=source.rows.getFirst();source.rows.set(0,new ContractBillingReportRepository.ServiceRow(row.id(),row.name(),row.description(),row.status(),amount,BigDecimal.ZERO,amount));
        var output=new ByteArrayOutputStream();new PdfBoxContractBillingPdfGenerator().write(data(null,1,1),source,output);
        try(var pdf=Loader.loadPDF(output.toByteArray())){String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("99.999.999.999.999.999,99"));}
    }
    @Test void preservesFullLongServiceDescriptionAcrossPagesAndDoesNotLeakSnapshotsWithoutCashAccess() throws Exception {
        var source=new Source(1,1);var row=source.rows.getFirst();
        String description="Descrição extensa de serviço com continuidade ".repeat(60)+" FIM-DA-DESCRICAO";
        source.rows.set(0,new ContractBillingReportRepository.ServiceRow(row.id(),row.name(),description,row.status(),row.commercialAmount(),row.invoicedAmount(),row.commercialBalance()));
        var output=new ByteArrayOutputStream();new PdfBoxContractBillingPdfGenerator().write(data(null,1,1),source,output);
        try(var pdf=Loader.loadPDF(output.toByteArray())) {
            var text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("FIM-DA-DESCRICAO"));assertTrue(pdf.getNumberOfPages()>1);
            assertTrue(text.contains("Número do contrato"));assertTrue(text.contains("Saldo total"));assertFalse(text.contains("Alocado s/NF"));
        }
    }
    @Test void followsHumanExampleWithDocumentaryBalances500250AndZero() throws Exception {
        var source=new Source(1,3);var row=source.rows.getFirst();
        source.rows.set(0,new ContractBillingReportRepository.ServiceRow(row.id(),"Serviço",null,"ACTIVE",new BigDecimal("1000.00"),new BigDecimal("1000.00"),BigDecimal.ZERO));
        for(int n=0;n<3;n++){var invoice=source.invoices.get(n);source.invoices.set(n,new ContractBillingReportRepository.Column(invoice.id(),invoice.number(),null,LocalDate.of(2026,n+1,1),"REGISTERED",new BigDecimal(n==0?"500.00":"250.00")));}
        var data=new ContractBillingPdfGenerator.Data(UUID.randomUUID(),UUID.randomUUID(),"Emitente sintético",null,null,"Cliente","Obra","1/2027","ACTIVE",CurrencyCode.BRL,Instant.parse("2026-10-29T12:00:00Z"),new ContractNetAmount(new BigDecimal("1000.00"),BigDecimal.ZERO,new BigDecimal("1000.00")),new BigDecimal("1000.00"),BigDecimal.ZERO,null,"1/2027");
        var output=new ByteArrayOutputStream();new PdfBoxContractBillingPdfGenerator().write(data,source,output);
        try(var pdf=Loader.loadPDF(output.toByteArray())) {
            var stripper=new PDFTextStripper();stripper.setSortByPosition(true);String text=stripper.getText(pdf);
            assertTrue(java.util.regex.Pattern.compile("NF-SINTETICA-1.*?500,00\\s+500,00",java.util.regex.Pattern.DOTALL).matcher(text).find(),text);
            assertTrue(java.util.regex.Pattern.compile("NF-SINTETICA-2.*?250,00\\s+250,00",java.util.regex.Pattern.DOTALL).matcher(text).find(),text);
            assertTrue(java.util.regex.Pattern.compile("NF-SINTETICA-3.*?250,00\\s+0,00",java.util.regex.Pattern.DOTALL).matcher(text).find(),text);
            assertFalse(text.contains("Total NF serviço"));assertFalse(text.contains("Total recebido em dinheiro"));assertTrue(text.contains("1/2027"));
        }
    }
    private void save(String name,byte[] bytes)throws IOException {var directory=Path.of("output/pdf");Files.createDirectories(directory);Files.write(directory.resolve(name),bytes);}
    private ContractBillingPdfGenerator.Data data(byte[] logo,int serviceCount,int invoiceCount) {
        return new ContractBillingPdfGenerator.Data(UUID.randomUUID(),UUID.randomUUID(),"Emitente de demonstração - identidade inteiramente sintética","00000000000000",logo,
            "Cliente de demonstração","Unidade de testes com identificação extensa para conferir a quebra de linha e o cabeçalho repetido",
            "DEMO / 2026 - Contrato sintético de serviços industriais","ACTIVE",CurrencyCode.BRL,Instant.parse("2026-10-29T12:00:00Z"),
            new ContractNetAmount(new BigDecimal(serviceCount).multiply(new BigDecimal("100.00")),new BigDecimal("50.00"),new BigDecimal(serviceCount).multiply(new BigDecimal("100.00")).subtract(new BigDecimal("50.00"))),new BigDecimal(invoiceCount==2?75:invoiceCount),BigDecimal.ZERO,new BigDecimal("15.00"),"DEMO/2026");
    }
    private static final class Source implements ContractBillingReportRepository {
        private final List<ServiceRow> rows=new ArrayList<>();private final List<Column> invoices=new ArrayList<>();
        int maxServiceBatch,maxCells;
        Source(int services,int count) {
            var weights=new java.util.LinkedHashMap<String,BigDecimal>();
            for(int n=1;n<=services;n++){var id=UUID.randomUUID();weights.put("BASE/"+id,new BigDecimal("100.00"));
                rows.add(new ServiceRow(id,"Serviço sintético "+n,"Descrição completa do serviço de demonstração.","ACTIVE",null,null,null));}
            var net=new BigDecimal(services).multiply(new BigDecimal("100.00")).subtract(services==0?BigDecimal.ZERO:new BigDecimal("50.00"));
            var shares=com.renovar.canteiro.io.receivables.domain.NetBillingDistribution.allocate(net,weights);
            for(int index=0;index<rows.size();index++) {
                var row=rows.get(index);var amount=shares.get("BASE/"+row.id());
                var issued=index==0?new BigDecimal(count==2?75:count):BigDecimal.ZERO;
                rows.set(index,new ServiceRow(row.id(),row.name(),row.description(),row.status(),amount,issued,amount.subtract(issued)));
            }
            for(int n=1;n<=count;n++)invoices.add(new Column(UUID.randomUUID(),"NF-SINTETICA-"+n,"A",LocalDate.of(2026,10,1),"REGISTERED",count==2?new BigDecimal(n==1?"50.00":"25.00"):BigDecimal.ONE));
        }
        public BigDecimal serviceSubtotal(UUID company,UUID contract){return new BigDecimal(rows.size()).multiply(new BigDecimal("100.00"));}
        public BigDecimal invoicedAmount(UUID company,UUID contract){return invoices.stream().map(Column::amount).reduce(BigDecimal.ZERO,BigDecimal::add);}
        public BigDecimal unassignedInvoicedAmount(UUID company,UUID contract){return BigDecimal.ZERO;}
        public void validateInvoiceOrigins(UUID company,UUID contract){ }
        public List<Column> invoicePage(UUID company,UUID contract,int offset,int limit,boolean history){return List.copyOf(invoices.subList(Math.min(offset,invoices.size()),Math.min(offset+limit,invoices.size())));}
        public List<ServiceRow> servicePage(UUID company,UUID contract,UUID after,int limit,ContractNetAmount official){int offset=0;if(after!=null){while(offset<rows.size()&&!rows.get(offset).id().equals(after))offset++;offset++;}var page=List.copyOf(rows.subList(Math.min(offset,rows.size()),Math.min(offset+limit,rows.size())));maxServiceBatch=Math.max(maxServiceBatch,page.size());return page;}
        public Map<CellKey,BigDecimal> cells(UUID company,UUID contract,List<UUID> columns,List<UUID> services){maxCells=Math.max(maxCells,columns.size()*(services.size()+1));var cells=new HashMap<CellKey,BigDecimal>();if(!rows.isEmpty()&&services.contains(rows.getFirst().id()))for(UUID id:columns)cells.put(new CellKey(rows.getFirst().id(),id),invoices.stream().filter(i->i.id().equals(id)).findFirst().orElseThrow().amount());return cells;}
        public List<OpenCharge> openChargePage(UUID company,UUID contract,int page,int size){return page==0?List.of(new OpenCharge("COBRANCA-COM-NF",LocalDate.of(2026,11,10),new BigDecimal("75.00"),new BigDecimal("15.00"),new BigDecimal("60.00"),"PARTIALLY_RECEIVED",2L),new OpenCharge("COBRANCA-SEM-NF",LocalDate.of(2026,11,10),new BigDecimal("30.00"),BigDecimal.ZERO,new BigDecimal("30.00"),"OPEN",0L)):List.of();}
        public List<NoteRow> invoiceHistoryPage(UUID company,UUID contract,int offset,int limit){return invoicePage(company,contract,offset,limit,true).stream().map(i->new NoteRow(i,"COBRANCA-COM-NF",i.number().endsWith("-2")?Instant.parse("2026-10-28T15:00:00Z"):null,i.number().endsWith("-2")?new BigDecimal("140.00"):null)).toList();}
    }
}
