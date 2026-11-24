package com.renovar.canteiro.io.receivables.infrastructure.reporting;

import com.renovar.canteiro.io.receivables.application.ContractBillingPdfGenerator;
import com.renovar.canteiro.io.receivables.application.WorkBillingPdfGenerator;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository;
import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository.Column;
import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository.ServiceRow;
import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository.CellKey;
import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository.NoteRow;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/** Bounded 100-service x 3-NF windows; PDFBox page streams use disk cache. */
@Component
public class PdfBoxContractBillingPdfGenerator implements ContractBillingPdfGenerator, WorkBillingPdfGenerator {
    @Override public Result write(Data data, ContractBillingReportRepository source, OutputStream output) throws IOException {
        try(var pdf=new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            var layout=new Layout(pdf,data,null);
            long services=0,invoices=0;
            try {
                int offset=0;boolean first=true;
                List<Column> columns;
                do {
                    columns=source.invoicePage(data.companyId(),data.contractId(),offset,3,false);
                    if(columns.isEmpty() && offset>0) break;
                    layout.matrix(columns,offset);
                    UUID cursor=null;List<ServiceRow> rows;
                    do {
                        rows=source.servicePage(data.companyId(),data.contractId(),cursor,100,data.commercial());
                        var cells=source.cells(data.companyId(),data.contractId(),columns.stream().map(Column::id).toList(),rows.stream().map(ServiceRow::id).toList());
                        for(var row:rows) {
                            layout.service(row,columns,cells);
                            if(first) services++;
                            cursor=row.id();
                        }
                    }while(rows.size()==100);
                    if(services==0) layout.paragraph("Nenhum serviço comercial cadastrado.",false);
                    layout.adjustment(columns,source.cells(data.companyId(),data.contractId(),columns.stream().map(Column::id).toList(),List.of()));
                    layout.blockTotals(columns);
                    offset+=columns.size();first=false;
                }while(columns.size()==3);
                layout.newSection("RESUMO DO CONTRATO");
                layout.table(new float[]{260,260,260},List.of("Valor total do contrato","Valor total faturado","Saldo total"));
                layout.drawRow(List.of(layout.money(data.commercial().netAmount()),layout.money(data.invoicedAmount()),layout.money(data.commercial().netAmount().subtract(data.invoicedAmount()))),false);
                layout.newSection("NOTAS FISCAIS");
                layout.table(new float[]{190,170,120,130,170},List.of("Parcela / referência","Número NF","Data emissão","Valor da NF","Saldo histórico*"));
                offset=0;UUID previousInvoice=null;List<NoteRow> notes;BigDecimal running=BigDecimal.ZERO;
                int sequence=0;
                do {
                    notes=source.invoiceHistoryPage(data.companyId(),data.contractId(),offset,100);
                    for(var note:notes) {
                        var invoice=note.invoice();
                        if(!invoice.id().equals(previousInvoice)) {
                            boolean registered=invoice.status().equals("REGISTERED");
                            if(registered){running=running.add(invoice.amount());sequence++;}
                            layout.drawRow(List.of((registered?sequence+"ª parcela":"Cancelada")+"\n"+optional(note.installmentReference()),invoice.number()+
                                (invoice.series()==null?"":" / "+invoice.series())+(registered?"":" (Cancelada)"),invoice.issuedOn().toString(),layout.money(invoice.amount()),
                                registered?layout.money(data.commercial().netAmount().subtract(running)):"Não aplicável"),false);
                            invoices++;previousInvoice=invoice.id();
                        } else layout.drawRow(List.of("Referência adicional: "+optional(note.installmentReference()),invoice.number(),"","",""),false);
                    }
                    offset+=notes.size();
                }while(notes.size()==100);
                layout.endTable();
                if(invoices==0) layout.paragraph("Nenhuma nota fiscal registrada ou cancelada.",false);
                layout.paragraph("* Saldo após cada NF registrada, por emissão e ID, na base contratual atual deste relatório. Não reconstrói aditivos passados. Canceladas fora do acumulado.",false);
                if(data.confirmedReceivedAmount()!=null) {
                    layout.newSection("COBRANÇAS EM ABERTO");
                    int chargePage=0;long chargeCount=0;List<ContractBillingReportRepository.OpenCharge> charges;
                    do {
                        charges=source.openChargePage(data.companyId(),data.contractId(),chargePage++,100);
                        for(var charge:charges){layout.charge(charge);chargeCount++;}
                    }while(charges.size()==100);
                    if(chargeCount==0) layout.paragraph("Nenhuma cobrança com saldo oficial pendente.",false);
                    layout.paragraph("Saldo dos títulos após retenções, créditos e baixas. Cobrança e NF não são receitas adicionais; cobranças sem NF também estão incluídas.",false);
                }
            } finally {layout.close();}
            int pages=pdf.getNumberOfPages();pdf.save(output);
            return new Result(services,invoices,pages);
        }
    }

    @Override
    public ExportResult writeWork(Header header, Contracts contracts, OutputStream output) throws IOException {
        try (var pdf = new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            long count;
            try (var layout = new Layout(pdf, null, header)) {
                layout.newSection("CONTRATOS VINCULADOS");
                layout.table(new float[]{245, 115, 145, 145, 130},
                        List.of("Nome do contrato", "Número do contrato", "Valor do contrato", "Valor faturado", "Saldo"));
                var totals = contracts.scan(contract -> {
                    try {
                        var currency = contract.currency();
                        layout.drawRow(List.of(contract.name(), optional(contract.reference()),
                                layout.money(contract.commercial().netAmount(), currency),
                                layout.money(contract.invoicedAmount(), currency),
                                layout.money(contract.documentaryBalance(), currency)), false);
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
                count = totals.stream().mapToLong(total -> total.contractCount()).sum();
                layout.endTable();
                if (count == 0) layout.paragraph("Nenhum contrato nesta obra.", false);
                layout.newSection("RESUMO TOTAL POR MOEDA");
                layout.table(new float[]{105, 225, 225, 225},
                        List.of("Moeda", "Valor dos contratos", "Valor faturado", "Saldo"));
                for (var total : totals) {
                    var currency = total.currency();
                    layout.drawRow(List.of(currency.name(), layout.money(total.contractNetAmount(), currency),
                            layout.money(total.invoicedAmount(), currency), layout.money(total.documentaryBalance(), currency)), false);
                }
                layout.endTable();
            } catch (UncheckedIOException failure) {
                throw failure.getCause();
            }
            pdf.save(output);
            return new ExportResult(count, pdf.getNumberOfPages());
        }
    }

    private static String optional(String text){return text==null||text.isBlank()?"Não informado":text;}
    private static final class Layout implements AutoCloseable {
        private static final PDFont REGULAR=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final PDFont BOLD=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDDocument pdf;private final Data data;private final Header workHeader;private final DecimalFormat format;
        private final PDImageXObject logo;
        private PDPageContentStream stream;private float y;
        private String section;private List<Column> matrixColumns;private int matrixOffset;
        private float[] tableWidths;private List<String> tableHeadings;
        Layout(PDDocument pdf, Data data, Header workHeader) {
            this.pdf=pdf;this.data=data;this.workHeader=workHeader;
            format=new DecimalFormat("#,##0.00",DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
            logo=data==null?null:logo(pdf,data.logo());
        }
        private static PDImageXObject logo(PDDocument pdf,byte[] bytes) {
            if(bytes==null||bytes.length==0||bytes.length>2*1024*1024) return null;
            try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);if(!readers.hasNext()) return null;
                var reader=readers.next();
                try {
                    reader.setInput(input,true,true);
                    if(reader.getWidth(0)<1||reader.getHeight(0)<1||reader.getWidth(0)>2000||reader.getHeight(0)>2000) return null;
                    var image=reader.read(0);return image==null?null:LosslessFactory.createFromImage(pdf,image);
                } finally {reader.dispose();}
            } catch(IOException|RuntimeException ignored){return null;}
        }
        String money(BigDecimal amount){return amount==null?"Indisponível":format.format(amount);}
        String money(BigDecimal amount, CurrencyCode currency) {
            return (amount.signum() < 0 ? "-" : "") + currency.getSymbol() + " " + money(amount.abs());
        }
        void matrix(List<Column> columns,int offset) throws IOException {
            matrixColumns=columns;matrixOffset=offset;section="SERVIÇOS - PARCELAS "+(columns.isEmpty()?"nenhuma":(offset+1)+" a "+(offset+columns.size()));
            page();
        }
        void newSection(String title) throws IOException {
            matrixColumns=null;tableWidths=null;tableHeadings=null;section=title;
            if(stream==null||y<150) page();else{y-=16;text(section,28,y,10,true);y-=20;}
        }
        private void page() throws IOException {
            close();var page=new PDPage(new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth()));pdf.addPage(page);
            stream=new PDPageContentStream(pdf,page);y=page.getMediaBox().getHeight()-28;
            text("Página "+pdf.getNumberOfPages(),page.getMediaBox().getWidth()-90,18,8,false);
            if (workHeader != null) {
                for (String line : wrap(workHeader.workName(), 780, 14, true)) {
                    text(line, 28, y, 14, true);
                    y -= 17;
                }
                header("Referência da obra: " + optional(workHeader.workReference()));
                y -= 8;
                text(section, 28, y, 10, true);
                y -= 20;
                if (tableHeadings != null) drawRow(tableHeadings, true);
                return;
            }
            if(logo!=null) {
                float scale=Math.min(75f/logo.getWidth(),38f/logo.getHeight());
                stream.drawImage(logo,28,y-38,logo.getWidth()*scale,logo.getHeight()*scale);
            }
            float nameX=logo==null?28:116;
            var issuer=wrap(data.issuer(),680,10,true);for(String line:issuer){text(line,nameX,y,10,true);y-=12;}
            String document=data.issuerDocument();
            String cnpj=document!=null?document.replaceAll("[^0-9A-Za-z]",""):"";
            if(cnpj.matches("[0-9A-Za-z]{12}[0-9]{2}")) cnpj=cnpj.substring(0,2)+"."+cnpj.substring(2,5)+"."+cnpj.substring(5,8)+"/"+cnpj.substring(8,12)+"-"+cnpj.substring(12);
            else cnpj="Não informado";
            text("CNPJ: "+cnpj,nameX,y,9,false);y-=22;
            y=Math.min(y,page.getMediaBox().getHeight()-88);
            String title="CONTROLE DE FATURAMENTO";
            text(title,(page.getMediaBox().getWidth()-width(title,14,true))/2,y,14,true);y-=20;
            header("Cliente: "+optional(data.customer()));header("Obra / unidade: "+optional(data.work()));
            header("Contrato: "+optional(data.contract())+" | Situação: "+contractStatus(data.contractStatus()));
            header("Moeda: "+data.currency()+" | Gerado em UTC: "+data.generatedAt());
            y-=5;text(section,28,y,10,true);y-=20;
            if(matrixColumns!=null) headings();else if(tableHeadings!=null) drawRow(tableHeadings,true);
        }
        private void header(String value) throws IOException {for(String line:wrap(value,780,9,false)){text(line,28,y,9,false);y-=11;}}
        private void headings() throws IOException {
            var headings=new ArrayList<String>();headings.add("Serviço");headings.add("Número do contrato");headings.add("Valor do serviço");
            for(int i=0;i<matrixColumns.size();i++){var c=matrixColumns.get(i);headings.add((matrixOffset+i+1)+"ª parcela");}
            headings.add("Saldo total");
            drawRow(headings,true);
        }
        private float[] widths(){
            if(tableWidths!=null)return tableWidths;
            int n=matrixColumns.size();float[] widths=new float[n+4];Arrays.fill(widths,110);widths[0]=230;widths[1]=110;
            if(n>0){float other=(780-230-110-110-110)/(float)n;for(int i=0;i<n;i++) widths[3+i]=other;}
            return widths;
        }
        void table(float[] widths,List<String> headings)throws IOException{tableWidths=widths;tableHeadings=headings;drawRow(headings,true);}
        void endTable(){tableWidths=null;tableHeadings=null;y-=16;}

        void service(ServiceRow row,List<Column> columns,Map<CellKey,BigDecimal> cells) throws IOException {
            var values=new ArrayList<String>();values.add(row.name()+(row.description()==null||row.description().isBlank()?"":"\n"+row.description())+(row.status().equals("CANCELLED")?" (Cancelado)":""));values.add(optional(data.contractNumber()));values.add(money(row.commercialAmount()));
            for(var column:columns) values.add(money(cells.getOrDefault(new CellKey(row.id(),column.id()),BigDecimal.ZERO)));
            values.add(money(row.commercialBalance()));drawRow(values,false);
        }
        void adjustment(List<Column> columns,Map<CellKey,BigDecimal> cells) throws IOException {
            if(data.unassignedInvoicedAmount().signum()==0) return;
            var values=new ArrayList<String>();values.add("Ajustes legados sem serviço (sem rateio)");values.add(optional(data.contractNumber()));values.add("Indisponível");
            for(var column:columns) values.add(money(cells.getOrDefault(new CellKey(null,column.id()),BigDecimal.ZERO)));
            values.add("Não aplicável");drawRow(values,false);
        }
        void blockTotals(List<Column> columns) throws IOException {
            var values=new ArrayList<String>();values.add("Totais: bloco NF / contrato");values.add(optional(data.contractNumber()));values.add(money(data.commercial().netAmount()));
            for(var column:columns) values.add(money(column.amount()));
            values.add(money(data.commercial().netAmount().subtract(data.invoicedAmount())));drawRow(values,true);
            y-=16;
            paragraph("Parcelas representam NFs registradas em ordem de emissão e ID. Não somar continuações.",false);
        }
        private void drawRow(List<String> values,boolean heading) throws IOException {
            float[] widths=widths();var wrapped=new ArrayList<List<String>>();var sizes=new ArrayList<Float>();int lines=1;
            for(int i=0;i<values.size();i++){
                float size=8;
                if(i>0 && values.get(i).matches("-?[0-9.,]+")) size=Math.min(8,8*(widths[i]-10)/Math.max(1,width(values.get(i),8,heading)));
                sizes.add(size);var parts=wrap(values.get(i),widths[i]-9,size,heading);wrapped.add(parts);lines=Math.max(lines,parts.size());
            }
            int start=0;
            while(start<lines) {
                if(y<72) page();
                int count=Math.min(lines-start,Math.max(1,(int)((y-42-10)/10)));
                float height=count*10+10,x=28;
                for(int i=0;i<values.size();i++) {
                    if(heading){stream.setNonStrokingColor(new java.awt.Color(229,237,244));stream.addRect(x,y-height,widths[i],height);stream.fill();}
                    stream.setStrokingColor(new java.awt.Color(190,200,210));stream.addRect(x,y-height,widths[i],height);stream.stroke();
                    float baseline=y-13;
                    var parts=wrapped.get(i);
                    for(int index=start;index<Math.min(start+count,parts.size());index++) {
                        String line=parts.get(index);float size=sizes.get(i);
                        float left=(i>0 || tableWidths!=null) && line.matches("-?[0-9.,]+")?x+widths[i]-5-width(line,size,heading):x+5;
                        text(line,left,baseline,size,heading);baseline-=10;
                    }
                    x+=widths[i];
                }
                y-=height;start+=count;
                if(start<lines) page();
            }
        }
        void charge(ContractBillingReportRepository.OpenCharge charge)throws IOException {
            String state=switch(charge.status()) {case "PARTIALLY_RECEIVED"->"Parcialmente recebida";case "OPEN"->"Em aberto";default->charge.status();};
            paragraph("Cobrança "+optional(charge.reference())+" | Vencimento: "+charge.dueOn()+" | "+state,true);
            paragraph("Cobrado: "+money(charge.charged())+" | Recebido em dinheiro: "+money(charge.received())+" | Saldo pendente: "+money(charge.outstanding())+
                " | "+(charge.invoiceCount()==null?"Vínculo de NF não atribuível ao título renegociado":charge.invoiceCount()==0?"Sem NF":charge.invoiceCount()+" NF(s) registrada(s) vinculada(s)"),false);
        }
        void paragraph(String value,boolean bold) throws IOException {
            for(String line:wrap(value,780,9,bold)){if(y<44) page();text(line,28,y,9,bold);y-=12;}y-=7;
        }
        private static String clean(String value) {
            if(value!=null && value.isEmpty())return "";
            StringBuilder text=new StringBuilder();
            for(char ch:optional(value).toCharArray()) {
                if(ch=='\n'){text.append(ch);continue;}if(Character.isISOControl(ch)){text.append(' ');continue;}
                try{REGULAR.encode(String.valueOf(ch));text.append(ch);}catch(IOException|IllegalArgumentException ignored){text.append('?');}
            }
            return text.toString();
        }
        private static float width(String value,float size,boolean bold) throws IOException{return (bold?BOLD:REGULAR).getStringWidth(value)*size/1000;}
        private static List<String> wrap(String value,float max,float size,boolean bold) throws IOException {
            var result=new ArrayList<String>();
            for(String paragraph:clean(value).split("\n",-1)) {
                StringBuilder line=new StringBuilder();
                for(String word:paragraph.split(" +")) {
                    if(!line.isEmpty() && width(line+" "+word,size,bold)>max){result.add(line.toString());line.setLength(0);}
                    if(!line.isEmpty()) line.append(' ');
                    for(char ch:word.toCharArray()) {
                        if(width(line.toString()+ch,size,bold)>max && !line.isEmpty()){result.add(line.toString());line.setLength(0);}
                        line.append(ch);
                    }
                }
                result.add(line.toString());
            }
            return result;
        }
        private static String contractStatus(String status) {
            return switch(status){case "DRAFT"->"Rascunho";case "OPEN"->"Aberto";case "ACTIVE"->"Ativo";case "COMPLETED"->"Concluído";case "FINALIZED"->"Finalizado";case "SUSPENDED"->"Suspenso";case "CANCELLED"->"Cancelado";default->status;};
        }
        private void text(String value,float x,float y,float size,boolean bold)throws IOException {
            stream.setNonStrokingColor(new java.awt.Color(25,42,58));stream.beginText();stream.setFont(bold?BOLD:REGULAR,size);stream.newLineAtOffset(x,y);stream.showText(clean(value));stream.endText();
        }
        @Override public void close()throws IOException{if(stream!=null){stream.close();stream=null;}}
    }
}
