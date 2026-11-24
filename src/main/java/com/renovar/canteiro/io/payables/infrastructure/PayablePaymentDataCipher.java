package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.PayablePaymentCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** JDK AES-256-GCM, random nonce and tenant/title authenticated context. No RH key reuse. */
@Component
public class PayablePaymentDataCipher implements PayablePaymentCipher {
    private final String secret;
    private final String jwtSecret;
    private final String employeeSecret;
    public PayablePaymentDataCipher(@Value("${security.payable-payment-data.encryption-key:}") String secret,
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${security.employee-payment-data.encryption-key}") String employeeSecret){
        this.secret=secret;this.jwtSecret=jwtSecret;this.employeeSecret=employeeSecret;
    }
    @Override public String encrypt(String value,String context){return Base64.getEncoder().encodeToString(encryptFile(value.getBytes(StandardCharsets.UTF_8),context));}
    @Override public String decrypt(String value,String context){
        try(var stream=decryptFile(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(value)),context)){
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception e){throw new IllegalStateException("Payment instructions cannot be decrypted");}
    }
    @Override public byte[] encryptFile(byte[] value,String context){
        try{
            byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
            var cipher=cipher(Cipher.ENCRYPT_MODE,nonce,context);byte[] encrypted=cipher.doFinal(value);
            byte[] result=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,result,0,nonce.length);
            System.arraycopy(encrypted,0,result,nonce.length,encrypted.length);return result;
        }catch(Exception e){throw new IllegalStateException("Payment encryption requires an independent configured key");}
    }
    @Override public InputStream decryptFile(InputStream value,String context){
        try{
            byte[] nonce=value.readNBytes(12);if(nonce.length!=12)throw new IllegalArgumentException();
            return new CipherInputStream(value,cipher(Cipher.DECRYPT_MODE,nonce,context));
        }catch(Exception e){try{value.close();}catch(java.io.IOException ignored){ }
            throw new IllegalStateException("Payment instructions cannot be decrypted");}
    }
    private Cipher cipher(int mode,byte[] nonce,String context)throws Exception{
        if(secret==null||secret.trim().length()<32||secret.trim().equals(jwtSecret.trim())||secret.trim().equals(employeeSecret.trim()))
            throw new IllegalStateException("Independent payment key is required");
        byte[] key=MessageDigest.getInstance("SHA-256").digest(secret.trim().getBytes(StandardCharsets.UTF_8));
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(("payables:v1:"+context).getBytes(StandardCharsets.UTF_8));return cipher;
    }
}
