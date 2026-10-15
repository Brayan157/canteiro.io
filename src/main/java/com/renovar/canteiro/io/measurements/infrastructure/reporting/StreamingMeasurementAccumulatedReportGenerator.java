package com.renovar.canteiro.io.measurements.infrastructure.reporting;

import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedLine;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedLineSource;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedReportData;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedReportGenerator;
import com.renovar.canteiro.io.measurements.application.MeasurementAccumulatedServiceSubtotal;
import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.infrastructure.branding.MeasurementCompanyBrandingImage;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;
import java.awt.Color;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.money;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.quantity;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.measure;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.factor;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.status;
import static com.renovar.canteiro.io.measurements.infrastructure.reporting.MeasurementReportPresentation.date;

@Component
public class StreamingMeasurementAccumulatedReportGenerator implements MeasurementAccumulatedReportGenerator {
    /** Historical export uses the same grid, with only the selected version's items and totals. */
    public com.renovar.canteiro.io.measurements.application.MeasurementExportResult writeVersionPdf(
            com.renovar.canteiro.io.measurements.application.MeasurementExportData data,
            com.renovar.canteiro.io.measurements.application.MeasurementItemSource rows,OutputStream output)throws IOException {
        var zero=com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedTotalsResponse.of(0,BigDecimal.ZERO,BigDecimal.ZERO,null);
        var finance=new com.renovar.canteiro.io.measurements.domain.MeasurementFinancialSummary(data.measurement().getId(),data.measurement().getContractId(),
                BigDecimal.ZERO,null,null,null,null,List.of(),"NO_CONTRACT",data.version().getUpdatedAt(),null,null,null,data.measurement().getCurrency());
        var header=new MeasurementAccumulatedReportData(data.measurement(),data.version(),data.companyBranding(),zero,zero,finance);
        try(PDDocument doc=new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            PdfTable table=new PdfTable(doc,header,data);table.newPage();
            int[] count={0};BigDecimal[] sums={BigDecimal.ZERO,BigDecimal.ZERO};boolean[] weighted={false};
            rows.consume(item->{table.item(new MeasurementAccumulatedLine(data.serviceIdentity(item.getId()),item.getServiceTitle(),item,data.version().getVersionNumber(),data.version().getStatus().name(),status(data.version().getStatus().name())));
                count[0]++;sums[0]=sums[0].add(item.getTotalAmount());if(item.getTotalWeightKg()!=null){weighted[0]=true;sums[1]=sums[1].add(item.getTotalWeightKg());}});
            table.serviceSubtotal();table.groupTitle=null;
            table.notes(data.version().getExternalAcceptanceNotes());
            var amounts=com.renovar.canteiro.io.measurements.domain.MeasurementVersionAmountCalculator.calculate(sums[1],sums[0],data.discount());
            table.summary("Total bruto",decimal(amounts.grossAmount()));
            table.summary("Desconto de cabeçalho",decimal(amounts.discountAmount()));
            if(data.discount()!=null&&data.discount().getDiscountType()==com.renovar.canteiro.io.measurements.domain.MeasurementDiscountType.PERCENTAGE){
                table.text("Desconto: "+quantity(data.discount().getDiscountValue())+"%",32,table.y-9,8,false);table.y-=22;}
            table.summary("Total líquido",decimal(amounts.netAmount()));
            table.text("Peso total: "+quantity(amounts.totalWeightKg())+(amounts.totalWeightKg()==null?"":" kg"),32,table.y-9,8,false);
            table.closePage();doc.save(output);output.flush();
            return new com.renovar.canteiro.io.measurements.application.MeasurementExportResult(count[0],amounts);
        }
    }
    @Override public long writePdf(MeasurementAccumulatedReportData data,MeasurementAccumulatedLineSource rows,OutputStream output) throws IOException {
        try(PDDocument doc=new PDDocument(IOUtils.createTempFileOnlyStreamCache())) {
            PdfTable table=new PdfTable(doc,data);table.newPage();
            long[] count={0};
            rows.consume(line->{table.item(line);if(line.item()!=null)count[0]++;});
            table.serviceSubtotal();
            table.totals();table.closePage();doc.save(output);output.flush();return count[0];
        }
    }
    @Override public long writeSpreadsheet(MeasurementAccumulatedReportData data,MeasurementAccumulatedLineSource rows,OutputStream output) throws IOException {
        SXSSFWorkbook workbook=new SXSSFWorkbook(100);
        try(workbook) {
            workbook.setCompressTempFiles(true);
            Sheet sheet=workbook.createSheet("Acumulado (somente leitura)");
            sheet.setDisplayGridlines(false);
            CellStyle title=style(workbook,true,IndexedColors.DARK_TEAL,IndexedColors.WHITE);
            title.setAlignment(HorizontalAlignment.CENTER);
            CellStyle group=style(workbook,true,IndexedColors.LIGHT_BLUE,IndexedColors.BLACK);
            group.setAlignment(HorizontalAlignment.CENTER);
            CellStyle accepted=style(workbook,false,IndexedColors.WHITE,IndexedColors.BLACK);
            CellStyle pending=style(workbook,false,IndexedColors.LEMON_CHIFFON,IndexedColors.BLACK);
            CellStyle acceptedNumber=workbook.createCellStyle();acceptedNumber.cloneStyleFrom(accepted);acceptedNumber.setAlignment(HorizontalAlignment.RIGHT);
            CellStyle pendingNumber=workbook.createCellStyle();pendingNumber.cloneStyleFrom(pending);pendingNumber.setAlignment(HorizontalAlignment.RIGHT);
            CellStyle summary=style(workbook,true,IndexedColors.GREY_25_PERCENT,IndexedColors.BLACK);
            int[] widths={9,22,60,14,10,18,22,18,24,24};
            for(int c=0;c<widths.length;c++)sheet.setColumnWidth(c,widths[c]*256);
            Row heading=sheet.createRow(0);heading.setHeightInPoints(44);
            for(int c=0;c<10;c++)heading.createCell(c);
            textRow(sheet,1,data.measurement().getTitle(),title);
            var normalized=data.branding()==null?null:MeasurementCompanyBrandingImage.normalize(data.branding().logoContent()).orElse(null);
            if(normalized!=null) {
                int image=workbook.addPicture(normalized.content(),normalized.format()==MeasurementCompanyBrandingImage.Format.PNG?Workbook.PICTURE_TYPE_PNG:Workbook.PICTURE_TYPE_JPEG);
                ClientAnchor anchor=workbook.getCreationHelper().createClientAnchor();anchor.setCol1(0);anchor.setRow1(0);
                var picture=sheet.createDrawingPatriarch().createPicture(anchor,image);
                var dimensions=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(normalized.content()));
                picture.resize(Math.min(170d/dimensions.getWidth(),50d/dimensions.getHeight()));
            } else {sheet.addMergedRegion(new CellRangeAddress(0,0,0,9));heading.getCell(0).setCellValue(data.branding()==null?"Empresa emitente":data.branding().displayName());heading.getCell(0).setCellStyle(group);}
            textRow(sheet,2,"Medição acumulada | Referência: "+display(data.measurement().getReference())+
                " | Avanço atual: "+data.currentVersion().getVersionNumber()+" | "+status(data.currentVersion().getStatus().name())+" | Moeda: "+data.measurement().getCurrency(),summary);
            textRow(sheet,3,"Aceitos: somente leitura. Em revisão: aguarda novo aceite. Este relatório não é uma planilha de importação.",summary);
            textRow(sheet,4,"Posição financeira em UTC: "+data.financialSummary().asOf(),summary);
            String symbol=data.measurement().getCurrency().getSymbol();
            String[] headers={"Item","Situação","Atividade / descrição","Data","Und","m ou m²","kg/m ou kg/m²","Peso (kg)",
                "Preço ("+symbol+")","Valor ("+symbol+")","Serviço","Tipo","Avanço","Item ID","Serviço ID","Moeda"};
            Row header=sheet.createRow(6);for(int c=0;c<headers.length;c++){Cell cell=header.createCell(c);cell.setCellValue(headers[c]);cell.setCellStyle(title);}header.setHeightInPoints(36);
            int[] row={7};long[] count={0};Object[] previous={null,null,null};
            rows.consume(line->{
                Object key=line.continuousServiceId()==null?"legacy:"+line.serviceTitle():line.continuousServiceId();
                if(!Objects.equals(previous[0],key)) {
                    if(previous[0]!=null)row[0]=serviceSubtotalRow(sheet,row[0],subtotal(data,(UUID)previous[1],(String)previous[2]),summary,data);
                    textRow(sheet,row[0]++,"SERVIÇO: "+line.serviceTitle(),group);
                    previous[0]=key;previous[1]=line.continuousServiceId();previous[2]=line.serviceTitle();
                }
                if(line.item()==null)return;
                MeasurementItem i=line.item();Row r=sheet.createRow(row[0]++);
                String[] values={line.versionNumber()+"."+i.getItemNumber(),status(line.status()),i.getActivity()+
                    (i.getDescription()==null?"":"\n"+i.getDescription()),date(i.getMeasuredOn()),quantity(i.getUnitQuantity()),measure(i),factor(i),quantity(i.getTotalWeightKg()),
                    money(i.getUnitPrice(),data.measurement().getCurrency()),money(i.isActive()?i.getTotalAmount():BigDecimal.ZERO,data.measurement().getCurrency()),
                    line.serviceTitle(),i.getChargeType().name(),Integer.toString(line.versionNumber()),i.getId()==null?"":i.getId().toString(),
                    line.continuousServiceId()==null?"":line.continuousServiceId().toString(),data.measurement().getCurrency().name()};
                for(int c=0;c<values.length;c++){Cell cell=r.createCell(c);cell.setCellValue(values[c]);cell.setCellStyle(line.status().equals("ACCEPTED")?(c>=4&&c<10?acceptedNumber:accepted):(c>=4&&c<10?pendingNumber:pending));}
                // Split extremely long descriptions over presentation-only continuation rows.
                // No content is truncated by Excel's 409-point maximum row height.
                String description=values[2];int chunkSize=1200;
                r.getCell(2).setCellValue(description.substring(0,Math.min(chunkSize,description.length())));
                r.setHeightInPoints(Math.max(38,Math.min(409,((Math.min(chunkSize,description.length())/52)+2)*13)));
                for(int start=chunkSize;start<description.length();start+=chunkSize){Row continuation=sheet.createRow(row[0]++);
                    Cell c=continuation.createCell(2);c.setCellValue(description.substring(start,Math.min(start+chunkSize,description.length())));
                    c.setCellStyle(line.status().equals("ACCEPTED")?accepted:pending);continuation.setHeightInPoints(338);}
                count[0]++;
            });
            if(previous[0]!=null)row[0]=serviceSubtotalRow(sheet,row[0],subtotal(data,(UUID)previous[1],(String)previous[2]),summary,data);
            textRow(sheet,++row[0],"Totais documentais oficiais",title);
            summaryRow(sheet,++row[0],"Bruto aceito",money(data.acceptedTotals().grossAmount(),data.measurement().getCurrency()),summary);
            summaryRow(sheet,++row[0],"Desconto aceito",money(new BigDecimal(data.acceptedTotals().grossAmount()).subtract(new BigDecimal(data.acceptedTotals().netAmount())),data.measurement().getCurrency()),summary);
            summaryRow(sheet,++row[0],"Líquido aceito",money(data.acceptedTotals().netAmount(),data.measurement().getCurrency()),summary);
            summaryRow(sheet,++row[0],"Variação líquida em revisão (não reconhecida)",money(data.pendingTotals().netAmount(),data.measurement().getCurrency()),summary);
            summaryRow(sheet,++row[0],"Peso aceito (kg)",data.acceptedTotals().totalWeightKg(),summary);
            textRow(sheet,++row[0],"Resumo financeiro da medição",title);
            summaryRow(sheet,++row[0],"Valor total da medição (aceito)",money(data.financialSummary().acceptedAmount(),data.measurement().getCurrency()),summary);
            summaryRow(sheet,++row[0],"Já recebido",money(data.financialSummary().receivedAmount(),data.measurement().getCurrency()),summary);
            summaryRow(sheet,++row[0],"Falta receber",money(data.financialSummary().remainingToReceive(),data.measurement().getCurrency()),summary);
            if(data.financialSummary().receivedAmount()==null)textRow(sheet,++row[0],unavailable(data.financialSummary().financialAvailability()),summary);
            if(data.financialSummary().financialAdjustmentAmount()!=null&&data.financialSummary().financialAdjustmentAmount().signum()!=0)
                summaryRow(sheet,++row[0],"Ajustes financeiros atribuídos (desconto/retenção/crédito)",money(data.financialSummary().financialAdjustmentAmount(),data.measurement().getCurrency()),summary);
            for(int c=10;c<18;c++)sheet.setColumnHidden(c,true);sheet.createFreezePane(0,7);
            sheet.setRepeatingRows(new CellRangeAddress(6,6,0,9));workbook.setPrintArea(0,0,9,0,row[0]);
            sheet.getPrintSetup().setLandscape(true);sheet.setFitToPage(true);sheet.getPrintSetup().setFitWidth((short)1);
            workbook.write(output);output.flush();return count[0];
        } finally {workbook.dispose();}
    }
    private static CellStyle style(Workbook workbook,boolean bold,IndexedColors fill,IndexedColors text){
        CellStyle s=workbook.createCellStyle();s.setFillForegroundColor(fill.getIndex());s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setWrapText(true);s.setVerticalAlignment(VerticalAlignment.TOP);Font font=workbook.createFont();font.setBold(bold);font.setColor(text.getIndex());font.setFontHeightInPoints((short)10);s.setFont(font);return s;
    }
    private static void textRow(Sheet sheet,int index,String text,CellStyle style){
        Row r=sheet.createRow(index);for(int c=0;c<10;c++)r.createCell(c).setCellStyle(style);
        r.getCell(0).setCellValue(text);sheet.addMergedRegion(new CellRangeAddress(index,index,0,9));r.setHeightInPoints(28);
    }
    private static void summaryRow(Sheet sheet,int index,String label,String value,CellStyle style){
        Row r=sheet.createRow(index);Cell l=r.createCell(0);l.setCellValue(label);l.setCellStyle(style);sheet.addMergedRegion(new CellRangeAddress(index,index,0,7));
        Cell v=r.createCell(8);v.setCellValue(value==null||value.equals("-")?"Indisponível":value);v.setCellStyle(style);sheet.addMergedRegion(new CellRangeAddress(index,index,8,9));r.setHeightInPoints(24);
    }
    private static MeasurementAccumulatedServiceSubtotal subtotal(MeasurementAccumulatedReportData data,UUID id,String title) {
        return data.serviceSubtotals().stream().filter(s->Objects.equals(s.continuousServiceId(),id)
            &&(id!=null||Objects.equals(s.serviceTitle(),title))).findFirst().orElse(null);
    }
    private static int serviceSubtotalRow(Sheet sheet,int index,MeasurementAccumulatedServiceSubtotal subtotal,CellStyle style,MeasurementAccumulatedReportData data) {
        if(subtotal==null)return index;
        summaryRow(sheet,index++,"Subtotal líquido aceito",money(subtotal.acceptedTotals().netAmount(),data.measurement().getCurrency()),style);
        if(subtotal.pendingTotals().itemCount()>0||new BigDecimal(subtotal.pendingTotals().netAmount()).signum()!=0)
            summaryRow(sheet,index++,"Variação líquida do serviço em revisão",money(subtotal.pendingTotals().netAmount(),data.measurement().getCurrency()),style);
        return index;
    }
    private static String display(Object value){return value==null?"-":value.toString();}
    private static String decimal(BigDecimal value){return value==null?"-":value.toPlainString();}
    private static String unavailable(String reason){return "NO_CONTRACT".equals(reason)?"Financeiro indisponível: medição sem contrato vinculado.":
        "LEGACY_ORIGIN_REQUIRES_RECONCILIATION".equals(reason)?"Financeiro indisponível: origem financeira antiga exige reconciliação.":"Financeiro indisponível: acesso financeiro não autorizado.";}
    private static final class PdfTable {
        private static final PDRectangle SIZE=new PDRectangle(PDRectangle.A4.getHeight(),PDRectangle.A4.getWidth());
        private static final org.apache.pdfbox.pdmodel.font.PDFont NORMAL=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private static final org.apache.pdfbox.pdmodel.font.PDFont BOLD=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private static final float LEFT=32,BOTTOM=38,LINE=10;
        private static final float[] WIDTHS={35,68,218,54,34,64,76,59,82,88};
        private final PDDocument doc;private final MeasurementAccumulatedReportData data;
        private final com.renovar.canteiro.io.measurements.application.MeasurementExportData versionData;
        private BigDecimal versionGroupGross=BigDecimal.ZERO;
        private PDPageContentStream stream;private float y;private int page;private Object groupKey;private String groupTitle;private UUID groupServiceId;
        PdfTable(PDDocument doc,MeasurementAccumulatedReportData data){this(doc,data,null);}
        PdfTable(PDDocument doc,MeasurementAccumulatedReportData data,com.renovar.canteiro.io.measurements.application.MeasurementExportData versionData){this.doc=doc;this.data=data;this.versionData=versionData;}
        void newPage() throws IOException{
            PDPage p=new PDPage(SIZE);doc.addPage(p);stream=new PDPageContentStream(doc,p);page++;
            float brandBottom=companyHeader();
            String title=clean(data.measurement().getTitle());float font=16;
            if(BOLD.getStringWidth(title)/1000*font>totalWidth()*0.90f)font=12;
            List<String> titleLines=wrap(title,totalWidth()*0.90f,font,BOLD);
            float titleTop=brandBottom-24;
            for(int n=0;n<titleLines.size();n++) {
                float width=BOLD.getStringWidth(titleLines.get(n))/1000*font;
                text(titleLines.get(n),(SIZE.getWidth()-width)/2,titleTop-n*(font+2),font,true);
            }
            float headerEnd=titleTop-(titleLines.size()-1)*(font+2)-22;
            text((versionData==null?"Medição acumulada":"Medição por versão")+" | Ref.: "+display(data.measurement().getReference())+" | Versão "+data.currentVersion().getVersionNumber()+" | "+status(data.currentVersion().getStatus().name())+" | Moeda: "+data.measurement().getCurrency(),LEFT,headerEnd,8,false);
            text(versionData==null?"Aceitos protegidos; atividades em revisão aguardam novo aceite. Posição UTC: "+data.financialSummary().asOf():
                "Data da medição: "+date(data.measurement().getMeasuredOn())+" | Aceite externo: "+date(data.currentVersion().getExternalAcceptanceOn()),LEFT,headerEnd-13,8,false);
            y=headerEnd-37;fill(LEFT,y-15,totalWidth(),32,new Color(0,105,95));
            String symbol=data.measurement().getCurrency().getSymbol();
            String[] headers={"Item","Situação","Atividade / descrição","Data","Und","m ou m²","kg/m ou kg/m²","Peso (kg)","Preço ("+symbol+")","Valor ("+symbol+")"};
            float x=LEFT;stream.setNonStrokingColor(Color.WHITE);for(int c=0;c<headers.length;c++){var labels=wrap(headers[c],WIDTHS[c]-6,8);for(int n=0;n<labels.size();n++)text(labels.get(n),x+3,y-n*10,8,true);x+=WIDTHS[c];}stream.setNonStrokingColor(Color.BLACK);
            y-=33;
            if(groupTitle!=null)group(groupTitle);
        }
        private float companyHeader()throws IOException {
            var branding=data.branding();
            var logo=branding==null?null:MeasurementCompanyBrandingImage.normalize(branding.logoContent()).orElse(null);
            float top=SIZE.getHeight()-28,nameX=LEFT,imageBottom=top;
            if(logo!=null){
                PDImageXObject image=PDImageXObject.createFromByteArray(doc,logo.content(),"private-company-logo");
                float ratio=Math.min(90f/image.getWidth(),32f/image.getHeight());
                imageBottom=top-image.getHeight()*ratio;
                stream.drawImage(image,LEFT,imageBottom,image.getWidth()*ratio,image.getHeight()*ratio);
                nameX+=106;
            }
            String name=branding==null?null:branding.displayName();
            String company=name==null||name.isBlank()?"Empresa emitente não informada":"Empresa emitente: "+name;
            float width=LEFT+totalWidth()-nameX;
            List<String> nameLines=wrap(company,width,9,BOLD);
            float baseline=top-9;
            for(String line:nameLines){text(line,nameX,baseline,9,true);baseline-=12;}
            baseline-=2;
            for(String line:wrap(cnpjLabel(branding==null?null:branding.document()),width,8)){
                text(line,nameX,baseline,8,false);baseline-=10;
            }
            return Math.min(imageBottom,baseline+8);
        }
        private String cnpjLabel(String document) {
            if(document==null||document.isBlank())return "CNPJ: não informado";
            String normalized=document.replaceAll("[.\\-/\\s]","").toUpperCase(java.util.Locale.ROOT);
            // Presentation only: retain registered numeric/alphanumeric CNPJ; never invent a tax identifier.
            if(!normalized.matches("[A-Z0-9]{12}[0-9]{2}"))return "CNPJ: não informado (documento cadastrado incompatível)";
            return "CNPJ: "+normalized.substring(0,2)+"."+normalized.substring(2,5)+"."+normalized.substring(5,8)
                    +"/"+normalized.substring(8,12)+"-"+normalized.substring(12);
        }
        void item(MeasurementAccumulatedLine line) throws IOException{
            Object key=line.continuousServiceId()==null?"legacy:"+line.serviceTitle():line.continuousServiceId();
            if(!Objects.equals(groupKey,key)){serviceSubtotal();groupKey=key;groupServiceId=line.continuousServiceId();groupTitle=line.serviceTitle();
                if(y<BOTTOM+48){closePage();newPage();}else group(groupTitle);}
            if(line.item()==null)return;
            MeasurementItem i=line.item();
            if(versionData!=null)versionGroupGross=versionGroupGross.add(i.getTotalAmount());
            String[] values={line.versionNumber()+"."+i.getItemNumber(),status(line.status()),i.getActivity()+(i.getDescription()==null?"":"\n"+i.getDescription()),
                date(i.getMeasuredOn()),quantity(i.getUnitQuantity()),measure(i),factor(i),quantity(i.getTotalWeightKg()),
                money(i.getUnitPrice(),data.measurement().getCurrency()),money(i.isActive()?i.getTotalAmount():BigDecimal.ZERO,data.measurement().getCurrency())};
            List<List<String>> cells=new ArrayList<>();int max=1;
            for(int c=0;c<values.length;c++){List<String> wrapped=wrap(values[c],WIDTHS[c]-8,8);cells.add(wrapped);max=Math.max(max,wrapped.size());}
            int consumed=0;
            while(consumed<max){
                if(y<BOTTOM+22){closePage();newPage();}
                int available=Math.max(1,(int)((y-BOTTOM-8)/LINE));int lines=Math.min(available,max-consumed);
                float height=lines*LINE+8;float x=LEFT;
                for(int c=0;c<values.length;c++){
                    fill(x,y-height+4,WIDTHS[c],height,line.status().equals("ACCEPTED")?Color.WHITE:new Color(255,249,218));
                    stream.setStrokingColor(new Color(215,220,220));stream.addRect(x,y-height+4,WIDTHS[c],height);stream.stroke();
                    for(int l=0;l<lines;l++){int index=consumed>0&&(c==0||c==1)?l:consumed+l;if(index<cells.get(c).size())text(cells.get(c).get(index),x+3,y-8-l*LINE,8,false);}
                    x+=WIDTHS[c];
                }
                y-=height;consumed+=lines;
                if(consumed<max){closePage();newPage();}
            }
        }
        void serviceSubtotal()throws IOException {
            if(groupKey==null)return;
            if(versionData!=null){summary("Subtotal bruto do serviço",versionGroupGross.setScale(2).toPlainString());versionGroupGross=BigDecimal.ZERO;return;}
            MeasurementAccumulatedServiceSubtotal subtotal=subtotal(data,groupServiceId,groupTitle);
            if(subtotal==null)return;
            if(y<BOTTOM+70){closePage();newPage();}
            y-=6;
            summary("Subtotal líquido aceito",subtotal.acceptedTotals().netAmount());
            if(subtotal.pendingTotals().itemCount()>0||new BigDecimal(subtotal.pendingTotals().netAmount()).signum()!=0)
                summary("Variação líquida do serviço em revisão",subtotal.pendingTotals().netAmount());
            y-=8;
        }
        void group(String title)throws IOException{
            if(y<BOTTOM+35){closePage();newPage();return;}
            List<String> lines=wrap("SERVIÇO: "+title,totalWidth()-8,9);
            float height=lines.size()*12+8;fill(LEFT,y-height+4,totalWidth(),height,new Color(214,235,231));
            for(int i=0;i<lines.size();i++){float width=BOLD.getStringWidth(lines.get(i))/1000*9;text(lines.get(i),LEFT+(totalWidth()-width)/2,y-9-i*12,9,true);}y-=height+3;
        }
        void totals()throws IOException{
            groupTitle=null;notes(data.currentVersion().getExternalAcceptanceNotes());if(y<BOTTOM+230){closePage();newPage();}y-=12;
            summary("Bruto aceito",data.acceptedTotals().grossAmount());
            summary("Desconto aceito",new BigDecimal(data.acceptedTotals().grossAmount()).subtract(new BigDecimal(data.acceptedTotals().netAmount())).toPlainString());
            summary("Líquido aceito (documental)",data.acceptedTotals().netAmount());
            summary("Variação líquida em revisão (não reconhecida)",data.pendingTotals().netAmount());
            text("Peso total aceito: "+quantity(data.acceptedTotals().totalWeightKg())+" kg",LEFT,y-5,8,false);y-=20;
            y-=10;
            summary("Valor total da medição (aceito)",decimal(data.financialSummary().acceptedAmount()));
            summary("Já recebido",data.financialSummary().receivedAmount()==null?"Indisponível":decimal(data.financialSummary().receivedAmount()));
            summary("Falta receber",data.financialSummary().remainingToReceive()==null?"Indisponível":decimal(data.financialSummary().remainingToReceive()));
            if(data.financialSummary().receivedAmount()==null){text(unavailable(data.financialSummary().financialAvailability()),LEFT,y-10,8,false);y-=20;}
            if(data.financialSummary().financialAdjustmentAmount()!=null&&data.financialSummary().financialAdjustmentAmount().signum()!=0)
                summary("Ajustes financeiros atribuídos",decimal(data.financialSummary().financialAdjustmentAmount()));
        }
        void summary(String label,String value)throws IOException{
            if(y<BOTTOM+35){closePage();newPage();}
            fill(LEFT+200,y-17,totalWidth()-200,22,new Color(225,234,232));text(label,LEFT+205,y-9,9,true);
            String formatted=value==null||value.equals("Indisponível")?"Indisponível":money(value,data.measurement().getCurrency());
            float width=BOLD.getStringWidth(clean(formatted))/1000*9;
            text(formatted,LEFT+totalWidth()-width-6,y-9,9,true);y-=25;
        }
        void notes(String value)throws IOException{
            if(value==null||value.isBlank())return;
            for(String line:wrap("Observações do aceite: "+value,totalWidth()-8,8)){
                if(y<BOTTOM+22){closePage();newPage();}
                text(line,LEFT+4,y-10,8,false);y-=12;
            }y-=8;
        }
        void closePage()throws IOException{text("Canteiro.io | Medição acumulada | Página "+page,LEFT,20,7,false);stream.close();}
        void fill(float x,float y,float w,float h,Color color)throws IOException{stream.setNonStrokingColor(color);stream.addRect(x,y,w,h);stream.fill();stream.setNonStrokingColor(Color.BLACK);}
        void text(String text,float x,float y,float size,boolean bold)throws IOException{stream.beginText();stream.setFont(bold?BOLD:NORMAL,size);stream.newLineAtOffset(x,y);stream.showText(clean(text));stream.endText();}
        List<String> wrap(String value,float max,float size)throws IOException{
            return wrap(value,max,size,NORMAL);
        }
        List<String> wrap(String value,float max,float size,PDFont font)throws IOException{
            List<String> result=new ArrayList<>();
            for(String paragraph:clean(value).split("\\n",-1)){
                StringBuilder current=new StringBuilder();
                for(int index=0;index<paragraph.length();index++){
                    char ch=paragraph.charAt(index);
                    if(font.getStringWidth(current.toString()+ch)/1000*size>max&&current.length()>0){
                        int space=current.lastIndexOf(" ");
                        if(space>0){result.add(current.substring(0,space));String rest=current.substring(space+1);current.setLength(0);current.append(rest);}
                        else {result.add(current.toString());current.setLength(0);}
                    }
                    current.append(ch);
                }result.add(current.toString());
            }return result.isEmpty()?List.of("-"):result;
        }
        String clean(String text){if(text==null)return "-";StringBuilder b=new StringBuilder();text.codePoints().forEach(c->{if(c=='\n'||c=='\r')b.append('\n');else if(c>=32&&c<=255||c==0x20AC)b.append((char)c);else b.append('?');});return b.toString();}
        float totalWidth(){float result=0;for(float width:WIDTHS)result+=width;return result;}
    }
}

