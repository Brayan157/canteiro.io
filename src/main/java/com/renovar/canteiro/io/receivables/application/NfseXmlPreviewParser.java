package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import org.springframework.stereotype.Component;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.w3c.dom.Document;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Assisted extraction of the declared national 1.01 profile, not XSD/signature/fiscal validation. */
@Component
public class NfseXmlPreviewParser {
    private static final String NS="http://www.sped.fazenda.gov.br/nfse";
    private static final String NF="/NFSe/infNFSe", DPS=NF+"/DPS/infDPS", VAL=DPS+"/valores", TAX=VAL+"/trib";
    private static final String ISSUED_AT_PATH = NF + "/dhProc";
    @Schema(name="InvoiceXmlWithholdingStatus")
    public enum WithholdingStatus { RETAINED, NOT_RETAINED, UNDETERMINED }
    @Schema(name="InvoiceXmlSourceValue",description="Source text and exact XML path; monetary values use canonical two-place decimals, percentages up to four places, never JSON numbers. No fiscal validity claim.")
    public record Field(String value,String sourcePath) { @Override public String toString(){return "InvoiceXmlSourceValue[redacted]";} }
    @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(name="InvoiceXmlHeader")
    public record Header(Field number,Field series,Field accessKey,Field issuedOn,Field processedAt,Field serviceDescription) { }
    @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(name="InvoiceXmlDps")
    public record Dps(Field number,Field series,Field issuedAt,Field competenceOn) { }
    @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(name="InvoiceXmlTotals")
    public record Totals(Field serviceGrossAmount,Field unconditionalDiscountAmount,Field conditionalDiscountAmount,
            Field totalWithheldAmount,Field netAmount,Field commercialAmountCandidate,Field informationalSimplesPercent) { }
    @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(name="InvoiceXmlTaxCandidate")
    public record TaxCandidate(String tax,Field baseAmount,Field ratePercent,Field amount,Field withholdingIndicator,WithholdingStatus withholdingStatus) { }
    @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(name="InvoiceXmlIssue")
    public record Issue(String code,String field,String message,boolean blocking) { }
    public record Parsed(Header header,Dps dps,Totals sourceTotals,List<TaxCandidate> taxCandidates,Map<String,Field> additionalTaxFields,List<Issue> issues,
            String issuerDocument,String customerDocument,boolean signaturePresent,boolean substitutionIndicated,
            Map<String,Field> fiscalFacts,Field substitutedAccessKey) {
        @Override public String toString(){return "NfseXmlPreview[redacted]";}
    }
    public Parsed parse(byte[] bytes){
        return parse(bytes, false);
    }

    Parsed parseFiscal(byte[] bytes) {
        return parse(bytes, true);
    }

    private Parsed parse(byte[] bytes, boolean includeFiscalFacts){
        if(bytes==null||bytes.length==0||bytes.length>10*1024*1024)throw new IllegalArgumentException("XML must contain 1 byte to 10 MiB");
        InvoiceAttachmentContentValidator.validate(InvoiceAttachmentType.XML,bytes);
        try{
            var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=factory.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External reference rejected");});
            Document doc=builder.parse(new ByteArrayInputStream(bytes));var root=doc.getDocumentElement();
            if(!"NFSe".equals(root.getLocalName())||!NS.equals(root.getNamespaceURI())||!"1.01".equals(root.getAttribute("versao")))
                throw new IllegalArgumentException("Only the national NFSe XML 1.01 preview profile is supported");
            Reader r=new Reader(doc);List<Issue> issues=new ArrayList<>();
            issues.add(new Issue("FISCAL_VALIDATION_NOT_PERFORMED","source","XML schema, signature, authorization and current fiscal status have not been verified",false));
            issues.add(new Issue("NF_SERIES_REQUIRES_CONFIRMATION","header.series","DPS series is not automatically the NF series",false));
            boolean unsupported=r.exists(NF+"/IBSCBS")||r.exists(DPS+"/IBSCBS");
            if(unsupported)issues.add(new Issue("UNSUPPORTED_TAX_STRUCTURE","source","IBS/CBS monetary reconciliation is outside this preview profile",true));
            boolean substitution=r.exists(DPS+"/subst");
            if(substitution)issues.add(new Issue("SUBSTITUTION_REQUIRES_REVIEW","source","Review the reported substitution; extraction remains available and no existing NF will be cancelled or replaced",false));
            Field number=r.text(NF+"/nNFSe",100);if(number==null)throw new IllegalArgumentException("NFSe number is missing");
            Field key=r.text(NF+"/@Id",100);if(key!=null&&key.value().matches("NFS[0-9]{50}"))key=new Field(key.value().substring(3),key.sourcePath());
            else {key=null;issues.add(new Issue("NF_KEY_REQUIRES_CONFIRMATION","header.accessKey","A supported NF access key was not proven",false));}
            Field processedAt = includeFiscalFacts ? r.text(ISSUED_AT_PATH, 40) : r.optionalTimestampText(ISSUED_AT_PATH);
            Field issuedOn = localIssueDate(processedAt);
            if (issuedOn == null) {
                issues.add(new Issue("NF_ISSUED_ON_REQUIRES_CONFIRMATION", "header.issuedOn",
                        "A valid NFS-e issue timestamp was not found; confirm the NF issue date", false));
                if (!includeFiscalFacts) processedAt = null;
            }
            var header=new Header(number,null,key,issuedOn,processedAt,r.text(DPS+"/serv/cServ/xDescServ",2000));
            var dps=new Dps(r.text(DPS+"/nDPS",100),r.text(DPS+"/serie",50),r.text(DPS+"/dhEmi",40),r.text(DPS+"/dCompet",10));
            Field gross=r.decimal(VAL+"/vServPrest/vServ",2),retained=r.decimal(NF+"/valores/vTotalRet",2),net=r.decimal(NF+"/valores/vLiq",2);
            Field unconditional=r.decimal(VAL+"/vDescCondIncond/vDescIncond",2),conditional=r.decimal(VAL+"/vDescCondIncond/vDescCond",2);
            Field commercial=null;
            if(!unsupported&&retained!=null&&net!=null&&gross!=null){
                BigDecimal value=new BigDecimal(retained.value()).add(new BigDecimal(net.value()));
                BigDecimal expected=new BigDecimal(gross.value()).subtract(unconditional==null?BigDecimal.ZERO:new BigDecimal(unconditional.value()))
                        .subtract(conditional==null?BigDecimal.ZERO:new BigDecimal(conditional.value()));
                if(value.signum()>0&&value.compareTo(expected)==0)
                    commercial=new Field(value.toPlainString(),net.sourcePath()+" + "+retained.sourcePath());
                else issues.add(new Issue("XML_TOTALS_REQUIRE_REVIEW","sourceTotals","Reported totals do not prove a supported positive commercial amount",true));
            }
            if(commercial==null)issues.add(new Issue("COMMERCIAL_AMOUNT_UNAVAILABLE","sourceTotals","Commercial amount is unavailable; do not infer it from a tax percentage or calculate it in the frontend",true));
            var totals=new Totals(gross,unconditional,conditional,retained,net,commercial,
                    r.decimal(TAX+"/totTrib/pTotTribSN",4));
            List<TaxCandidate> taxes=new ArrayList<>();Field issCode=r.text(TAX+"/tribMun/tpRetISSQN",3);
            WithholdingStatus iss=issCode==null?WithholdingStatus.UNDETERMINED:switch(issCode.value()){
                case "1" -> WithholdingStatus.NOT_RETAINED;case "2","3" -> WithholdingStatus.RETAINED;default -> WithholdingStatus.UNDETERMINED;};
            taxes.add(new TaxCandidate("ISSQN",r.decimal(NF+"/valores/vBC",2),r.decimal(NF+"/valores/pAliqAplic",4),r.decimal(NF+"/valores/vISSQN",2),issCode,iss));
            String federal=TAX+"/tribFed",pc=federal+"/piscofins";Field pcCode=r.text(pc+"/tpRetPisCofins",3);
            for(String name:List.of("PIS","COFINS")){
                Field amount=r.decimal(pc+(name.equals("PIS")?"/vPis":"/vCofins"),2);
                Field rate=r.decimal(pc+(name.equals("PIS")?"/pAliqPis":"/pAliqCofins"),4);
                if(amount!=null||rate!=null||pcCode!=null)taxes.add(new TaxCandidate(name,r.decimal(pc+"/vBCPisCofins",2),rate,amount,pcCode,WithholdingStatus.UNDETERMINED));
            }
            for(String name:List.of("CP","IRRF","CSLL")){
                Field amount=r.decimal(federal+"/vRet"+name,2);
                if(amount!=null)taxes.add(new TaxCandidate(name,null,null,amount,null,WithholdingStatus.UNDETERMINED));
            }
            // Never reconstruct components or allocate the aggregate withholding.
            if(retained!=null&&new BigDecimal(retained.value()).signum()>0){
                BigDecimal proven=taxes.stream().filter(t->t.withholdingStatus()==WithholdingStatus.RETAINED&&t.amount()!=null)
                        .map(t->new BigDecimal(t.amount().value())).reduce(BigDecimal.ZERO,BigDecimal::add);
                if(proven.compareTo(new BigDecimal(retained.value()))!=0)issues.add(new Issue("WITHHOLDING_BREAKDOWN_INCOMPLETE","taxCandidates","Aggregate withholding is not fully explained by proven components; review without inventing taxes or allocation",false));
            }
            if(issCode!=null&&"3".equals(issCode.value()))issues.add(new Issue("INTERMEDIARY_WITHHOLDING_REQUIRES_REVIEW","taxCandidates","Reported intermediary withholding is not automatically customer withholding in this system",false));
            Map<String,Field> additional=new java.util.LinkedHashMap<>();
            Field declared=r.decimal(TAX+"/tribMun/pAliq",4),cst=r.text(pc+"/CST",4);
            if(declared!=null)additional.put("declaredIssRate",declared);if(cst!=null)additional.put("pisCofinsCst",cst);
            Map<String,Field> fiscalFacts=new java.util.LinkedHashMap<>();
            Field substitutedKey=null;
            if(includeFiscalFacts){
                fact(fiscalFacts,"nationalServiceCode",r.code(DPS+"/serv/cServ/cTribNac",6));
                fact(fiscalFacts,"municipalServiceCode",r.code(DPS+"/serv/cServ/cTribMun",20));
                fact(fiscalFacts,"nbsCode",r.code(DPS+"/serv/cServ/cNBS",9));
                fact(fiscalFacts,"declaredDeductionAmount",r.decimal(VAL+"/vDedRed/vDR",2));
                fact(fiscalFacts,"declaredDeductionPercent",r.decimal(VAL+"/vDedRed/pDR",4));
                fact(fiscalFacts,"substitutionReasonCode",r.code(DPS+"/subst/cMotivo",3));
                substitutedKey=r.code(DPS+"/subst/chSubstda",50);
            }
            return new Parsed(header,dps,totals,List.copyOf(taxes),Map.copyOf(additional),List.copyOf(issues),r.party(NF+"/emit"),r.party(DPS+"/toma"),
                    doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#","Signature").getLength()>0,substitution,
                    Map.copyOf(fiscalFacts),substitutedKey);
        }catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("XML cannot be read by the supported preview profile");}
    }

    private static Field localIssueDate(Field timestamp) {
        if (timestamp == null || !timestamp.value().matches(
                "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?(Z|[+-][0-9]{2}:[0-9]{2})")) {
            return null;
        }
        try {
            return new Field(OffsetDateTime.parse(timestamp.value()).toLocalDate().toString(), timestamp.sourcePath());
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    private void fact(Map<String,Field> facts,String name,Field value) {
        if(value!=null)facts.put(name,value);
    }
    private static final class Reader {
        private final Document doc;
        private final javax.xml.xpath.XPath xpath;
        Reader(Document doc){this.doc=doc;xpath=XPathFactory.newInstance().newXPath();xpath.setNamespaceContext(new javax.xml.namespace.NamespaceContext(){
            @Override public String getNamespaceURI(String prefix){return "n".equals(prefix)?NS:XMLConstants.NULL_NS_URI;}
            @Override public String getPrefix(String uri){return NS.equals(uri)?"n":null;}
            @Override public java.util.Iterator<String> getPrefixes(String uri){return List.of("n").iterator();}
        });}
        private org.w3c.dom.Node node(String path)throws Exception{
            String expression=path.replaceAll("/([A-Za-z][A-Za-z0-9_]*)","/n:$1");
            var nodes=(org.w3c.dom.NodeList)xpath.evaluate(expression,doc,XPathConstants.NODESET);
            if(nodes.getLength()>1)throw new IllegalArgumentException("XML has ambiguous repeated preview fields");return nodes.getLength()==0?null:nodes.item(0);
        }
        boolean exists(String path)throws Exception{return node(path)!=null;}
        Field text(String path,int max)throws Exception{
            var node=node(path);if(node==null)return null;String value=node.getTextContent().trim();if(value.isEmpty())return null;
            if(value.length()>max||value.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new IllegalArgumentException("XML preview field exceeds its safe format or size");
            return new Field(value.replaceAll("[\\p{Cntrl}]"," "),path);
        }
        Field optionalTimestampText(String path)throws Exception {
            var value = node(path);
            if (value == null) return null;
            String text = value.getTextContent().trim();
            return text.length() > 40 ? null : new Field(text, path);
        }
        Field decimal(String path,int scale)throws Exception{
            Field field=text(path,30);if(field==null)return null;
            if(!field.value().matches("[0-9]{1,17}(\\.[0-9]{1,4})?"))throw new IllegalArgumentException("Invalid XML decimal field");
            try{BigDecimal value=new BigDecimal(field.value()).setScale(scale,RoundingMode.UNNECESSARY);return new Field(value.toPlainString(),path);}
            catch(ArithmeticException e){throw new IllegalArgumentException("XML decimal precision is unsupported");}
        }
        Field code(String path,int max)throws Exception{
            Field field=text(path,max);
            if(field!=null&&!field.value().matches("[0-9]+"))throw new IllegalArgumentException("Invalid XML fiscal code");
            return field;
        }
        String party(String path)throws Exception{Field cnpj=text(path+"/CNPJ",14),cpf=text(path+"/CPF",11);
            if(cnpj!=null&&cpf!=null)throw new IllegalArgumentException("Ambiguous XML party document");return cnpj!=null?cnpj.value():cpf==null?null:cpf.value();}
    }
}
