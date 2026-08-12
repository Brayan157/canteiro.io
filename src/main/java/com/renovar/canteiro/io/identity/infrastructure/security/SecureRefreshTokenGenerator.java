package com.renovar.canteiro.io.identity.infrastructure.security;

import com.renovar.canteiro.io.identity.application.RefreshTokenGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SecureRefreshTokenGenerator implements RefreshTokenGenerator {

    private static final int TOKEN_SIZE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] tokenBytes = new byte[TOKEN_SIZE_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
