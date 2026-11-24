package com.renovar.canteiro.io.receivables.application;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NfseXmlPreviewParserTest {
    final NfseXmlPreviewParser parser=new NfseXmlPreviewParser();
    @Test void extractsSyntheticNationalDataWithProvenanceAndSeparatesDpsFromNf(){
        var result=parser.parse(xml("2","100.00","100.00","900.00",""));
        assertEquals("17",result.header().number().value());assertEquals("991",result.dps().number().value());
        assertNull(result.header().series());assertNull(result.header().issuedOn());
        assertEquals("1000.00",result.sourceTotals().commercialAmountCandidate().value());
        assertEquals("100.00",result.taxCandidates().getFirst().amount().value());
        assertEquals(NfseXmlPreviewParser.WithholdingStatus.RETAINED,result.taxCandidates().getFirst().withholdingStatus());
        assertTrue(result.taxCandidates().getFirst().amount().sourcePath().endsWith("vISSQN"));
    }
    @Test void usesTheIssueDateFromDhProcInTheOffsetWrittenInTheXml(){
        var result=parser.parse(xmlWithIssueTimestamp("2026-11-02T23:55:01.125-03:00"));
        assertEquals("2026-11-02",result.header().issuedOn().value());
        assertEquals("/NFSe/infNFSe/dhProc",result.header().issuedOn().sourcePath());
        assertEquals("2026-11-02T23:55:01.125-03:00",result.header().processedAt().value());
        assertEquals("2026-11-01",result.dps().competenceOn().value());
        assertEquals("2026-11-01T12:00:00-03:00",result.dps().issuedAt().value());
        assertTrue(result.issues().stream().noneMatch(i->i.code().equals("NF_ISSUED_ON_REQUIRES_CONFIRMATION")));
    }
    @Test void asksForManualConfirmationWhenTheIssueTimestampIsMissingOrInvalid(){
        for(String timestamp:new String[]{null,"", "2026-02-30T13:30:00-03:00", "2026-11-02T13:30:00",
                "2026-11-02", "2026-11-02T13:30:00+19:00", "x".repeat(80)}){
            var result=parser.parse(xmlWithIssueTimestamp(timestamp));
            assertNull(result.header().issuedOn());
            assertNull(result.header().processedAt());
            assertTrue(result.issues().stream().anyMatch(i->i.code().equals("NF_ISSUED_ON_REQUIRES_CONFIRMATION")&&!i.blocking()));
        }
    }
    private byte[] xmlWithIssueTimestamp(String timestamp){
        String base=new String(xml("2","100.00","100.00","900.00",""),StandardCharsets.UTF_8);
        return base.replace("<infNFSe>","<infNFSe>"+tag("dhProc",timestamp)).getBytes(StandardCharsets.UTF_8);
    }
    @Test void missingRetentionIsUndeterminedAndDoesNotBecomeZeroOrAnObligation(){
        var result=parser.parse(xml(null,null,null,"900.00",""));
        assertEquals(NfseXmlPreviewParser.WithholdingStatus.UNDETERMINED,result.taxCandidates().getFirst().withholdingStatus());
        assertNull(result.sourceTotals().totalWithheldAmount());assertNull(result.sourceTotals().commercialAmountCandidate());
        assertTrue(result.issues().stream().anyMatch(i->i.code().equals("COMMERCIAL_AMOUNT_UNAVAILABLE")));
        assertEquals("6.0000",result.sourceTotals().informationalSimplesPercent().value());
    }
    @Test void reportsNotRetainedWithoutInferringCompanyPayableAndFlagsAggregateWithoutBreakdown(){
        var result=parser.parse(xml("1","20.00","100.00","900.00",""));
        assertEquals(NfseXmlPreviewParser.WithholdingStatus.NOT_RETAINED,result.taxCandidates().getFirst().withholdingStatus());
        assertTrue(result.issues().stream().anyMatch(i->i.code().equals("WITHHOLDING_BREAKDOWN_INCOMPLETE")));
    }
    @Test void substitutionSignatureAndUnsupportedIbsDoNotAuthorizeFinancialActions(){
        var result=parser.parse(xml("3","100.00","100.00","900.00","<subst><chSubstda>synthetic-reference</chSubstda></subst><IBSCBS/>"));
        assertTrue(result.substitutionIndicated());assertTrue(result.issues().stream().anyMatch(NfseXmlPreviewParser.Issue::blocking));
        assertNull(result.sourceTotals().commercialAmountCandidate());
    }
    @Test void substitutionAloneDoesNotSuppressUsefulExtractedFieldsOrAuthorizeReplacement(){
        var result=parser.parse(xml("2","100.00","100.00","900.00","<subst><chSubstda>synthetic-reference</chSubstda></subst>"));
        assertEquals("17",result.header().number().value());assertEquals("1000.00",result.sourceTotals().commercialAmountCandidate().value());
        assertTrue(result.substitutionIndicated());assertTrue(result.issues().stream().filter(i->i.code().equals("SUBSTITUTION_REQUIRES_REVIEW")).noneMatch(NfseXmlPreviewParser.Issue::blocking));
    }
    @Test void rejectsDtdXxeXincludeUnsupportedNamespaceDuplicateFieldsAndDecimalPrecision(){
        String body=new String(xml("1","100.00","0.00","1000.00",""),StandardCharsets.UTF_8);
        for(String invalid:new String[]{"<!DOCTYPE NFSe [<!ENTITY x SYSTEM 'file:///private'>]>"+body,
                body.replace("<nNFSe>17</nNFSe>","<nNFSe>17</nNFSe><nNFSe>18</nNFSe>"),
                body.replace("http://www.sped.fazenda.gov.br/nfse","https://unsupported.example"),
                body.replace("<vISSQN>100.00</vISSQN>","<vISSQN>100.001</vISSQN>"),
                body.replace("<infNFSe>","<infNFSe><xi:include xmlns:xi='http://www.w3.org/2001/XInclude' href='file:///private'/>"),
                body.replace("versao='1.01'","versao='9.99'")})
            assertThrows(IllegalArgumentException.class,()->parser.parse(invalid.getBytes(StandardCharsets.UTF_8)));
    }
    public static byte[] xml(String code,String iss,String totalRet,String net,String extra){
        String xml="<NFSe xmlns='http://www.sped.fazenda.gov.br/nfse' versao='1.01'><infNFSe><nNFSe>17</nNFSe><emit><CNPJ>12345678000190</CNPJ></emit><valores><vBC>1000.00</vBC><pAliqAplic>10.00</pAliqAplic>"
                +tag("vISSQN",iss)+tag("vTotalRet",totalRet)+tag("vLiq",net)+"</valores><DPS><infDPS><nDPS>991</nDPS><serie>DPS9</serie><dhEmi>2026-11-01T12:00:00-03:00</dhEmi><dCompet>2026-11-01</dCompet>"
                +"<toma><CNPJ>12345678000195</CNPJ></toma>"+extra+"<valores><vServPrest><vServ>1000.00</vServ></vServPrest><trib><tribMun>"+tag("tpRetISSQN",code)
                +"</tribMun><totTrib><pTotTribSN>6.00</pTotTribSN></totTrib></trib></valores></infDPS></DPS></infNFSe></NFSe>";
        return xml.getBytes(StandardCharsets.UTF_8);
    }
    private static String tag(String name,String value){return value==null?"":"<"+name+">"+value+"</"+name+">";}
}
