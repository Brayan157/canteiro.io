package com.renovar.canteiro.io.employees.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class EmployeePaymentDataCipher {
    private static final int MINIMUM_SECRET_LENGTH = 32;

    private final SecretKeySpec key;

    @Autowired
    public EmployeePaymentDataCipher(@Value("${security.employee-payment-data.encryption-key}") String secret,
                                     @Value("${security.jwt.secret}") String jwtSecret) {
        validateSecret(secret);
        if (jwtSecret != null && MessageDigest.isEqual(
                secret.trim().getBytes(StandardCharsets.UTF_8), jwtSecret.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Employee payment encryption key must differ from the JWT signing secret");
        }
        key = keyFrom(secret);
    }

    private EmployeePaymentDataCipher(String secret) {
        validateSecret(secret);
        key = keyFrom(secret);
    }

    static EmployeePaymentDataCipher legacyJwtCipherForMigration(String secret) {
        return new EmployeePaymentDataCipher(secret);
    }
    public String encrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.trim().getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length]; System.arraycopy(iv, 0, payload, 0, iv.length); System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception exception) { throw new IllegalStateException("Employee payment data cannot be encrypted", exception); }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length <= 12) throw new IllegalArgumentException("Invalid encrypted employee payment data");
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Employee payment data cannot be decrypted", exception);
        }
    }

    private static SecretKeySpec keyFrom(String secret) {
        try {
            return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.trim().getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("Employee payment encryption cannot be configured", exception);
        }
    }

    private static void validateSecret(String secret) {
        if (secret == null || secret.isBlank() || secret.trim().length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException("Employee payment encryption key must contain at least 32 characters");
        }
    }
}
