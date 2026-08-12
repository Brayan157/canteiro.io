package com.renovar.canteiro.io.platform.subscription.application;

import java.util.UUID;

public record PaymentGatewayCustomerRequest(
        UUID companyId,
        String name,
        String document,
        String email,
        String phone
) {

    public PaymentGatewayCustomerRequest {
        if (companyId == null) {
            throw new IllegalArgumentException("A payment gateway customer requires a company");
        }
        name = requireText(name, "A payment gateway customer name is required");
        document = requireText(document, "A payment gateway customer document is required");
        email = normalizeOptional(email);
        phone = normalizeOptional(phone);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
