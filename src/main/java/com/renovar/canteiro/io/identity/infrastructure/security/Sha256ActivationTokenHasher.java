package com.renovar.canteiro.io.identity.infrastructure.security;

import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class Sha256ActivationTokenHasher implements ActivationTokenHasher {

    @Override
    public String hash(String rawActivationToken) {
        try {
            byte[] hashedToken = MessageDigest.getInstance("SHA-256")
                    .digest(rawActivationToken.getBytes(StandardCharsets.UTF_8));
            return toHex(hashedToken);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hexadecimal = new StringBuilder(bytes.length * 2);
        for (byte currentByte : bytes) {
            hexadecimal.append(String.format("%02x", currentByte));
        }
        return hexadecimal.toString();
    }
}
