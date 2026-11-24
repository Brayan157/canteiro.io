package com.renovar.canteiro.io.payables;

import com.renovar.canteiro.io.payables.domain.PayablePaymentContent;
import com.renovar.canteiro.io.payables.domain.PayablePaymentInstructionType;
import com.renovar.canteiro.io.payables.infrastructure.PayablePaymentDataCipher;
import org.junit.jupiter.api.Test;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayablePaymentContentTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void allowsPdfOnlyOrOneBoletoReferenceWithoutInferringBankValidity(){
        assertEquals(PayablePaymentInstructionType.BOLETO,content(Map.of("type","BOLETO")).type());
        assertEquals("1".repeat(47),content(Map.of("type","BOLETO","digitableLine","1".repeat(47))).digitableLine());
        assertThrows(Exception.class,()->content(Map.of("type","BOLETO","digitableLine","1".repeat(47),"barcode","1".repeat(44))));
        assertThrows(Exception.class,()->content(Map.of("type","BOLETO","barcode","123")));
    }
    @Test void bankInstructionsRequireConfirmedUserInputAndKeepSeparateDigits(){
        var c=content(Map.of("type","BANK_TRANSFER","bankCode","001","branch","1234","branchCheckDigit","X",
                "accountNumber","12345","accountCheckDigit","6","accountType","CHECKING","holderName","Titular informado"));
        assertEquals("12345",c.accountNumber());assertEquals("6",c.accountCheckDigit());assertEquals("X",c.branchCheckDigit());
        assertThrows(Exception.class,()->content(Map.of("type","BANK_TRANSFER","bankName","Banco","accountNumber","1")));
        assertThrows(Exception.class,()->content(Map.of("type","BANK_TRANSFER","bankCode","001","branch","1","accountNumber","1","accountType","INVALID","holderName","Titular")));
    }
    @Test void pixAcceptsSuppliedKeyOrCopyPasteWithoutGeneratingPayloadOrInferringHolder(){
        assertEquals("texto informado pelo usuário",content(Map.of("type","PIX","pixCopyPaste","texto informado pelo usuário")).pixCopyPaste());
        assertEquals("+5562999990000",content(Map.of("type","PIX","pixKeyType","PHONE","pixKey","+5562999990000")).pixKey());
        assertThrows(Exception.class,()->content(Map.of("type","PIX","pixKeyType","EMAIL","pixKey","bad")));
        assertThrows(Exception.class,()->content(Map.of("type","PIX","pixKeyType","RANDOM","pixKey","bad")));
        assertThrows(Exception.class,()->content(Map.of("type","PIX","pixKeyType","EMAIL","pixKey","a@example.com","pixCopyPaste","ambiguous")));
        assertThrows(Exception.class,()->content(Map.of("type","PIX","pixCopyPaste","text","holderName","Não permitido")));
    }
    @Test void randomNonceProtectsPlaintextAndTenantContextAndRejectsSharedOrMissingKeys(){
        var c=new PayablePaymentDataCipher("independent-payable-key-of-at-least-32-characters","different-jwt-secret","different-rh-secret");
        String first=c.encrypt("private-value","tenant/title");String second=c.encrypt("private-value","tenant/title");
        assertNotEquals(first,second);assertFalse(first.contains("private-value"));assertEquals("private-value",c.decrypt(first,"tenant/title"));
        assertThrows(IllegalStateException.class,()->c.decrypt(first,"other-tenant/title"));
        assertThrows(IllegalStateException.class,()->new PayablePaymentDataCipher("","jwt","rh").encrypt("private","context"));
        String reused="same-key-of-at-least-thirty-two-characters";
        assertThrows(IllegalStateException.class,()->new PayablePaymentDataCipher(reused,reused,"rh").encrypt("private","context"));
        assertThrows(IllegalStateException.class,()->new PayablePaymentDataCipher(reused,"jwt",reused).encrypt("private","context"));
    }
    @Test void protectsPdfBytesAndRejectsTamperedFile(){
        var c=new PayablePaymentDataCipher("independent-payable-key-of-at-least-32-characters","jwt","rh");
        byte[] encrypted=c.encryptFile("%PDF-private-document".getBytes(java.nio.charset.StandardCharsets.UTF_8),"tenant/title");encrypted[encrypted.length-1]^=1;
        assertThrows(java.io.IOException.class,()->{try(var stream=c.decryptFile(new java.io.ByteArrayInputStream(encrypted),"tenant/title")){stream.readAllBytes();}});
    }
    PayablePaymentContent content(Map<String,String> values){return json.readValue(json.writeValueAsString(values),PayablePaymentContent.class);}
}
