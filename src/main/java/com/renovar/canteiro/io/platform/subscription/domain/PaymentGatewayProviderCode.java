package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.Locale;

public record PaymentGatewayProviderCode(String value) {

    public PaymentGatewayProviderCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Payment gateway provider code is required");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.length() > 30 || !value.matches("[A-Z0-9_]+")) {
            throw new IllegalArgumentException("Payment gateway provider code is invalid");
        }
    }
}
