package com.renovar.canteiro.io.platform.subscription.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record PaymentGatewayWebhookRequest(
        byte[] rawPayload,
        Map<String, String> headers,
        Instant receivedAt
) {

    public PaymentGatewayWebhookRequest {
        if (rawPayload == null || rawPayload.length == 0) {
            throw new IllegalArgumentException("A payment gateway webhook payload is required");
        }
        if (headers == null || receivedAt == null) {
            throw new IllegalArgumentException("Payment gateway webhook headers and receipt time are required");
        }
        rawPayload = rawPayload.clone();
        headers = normalizeHeaders(headers);
    }

    @Override
    public byte[] rawPayload() {
        return rawPayload.clone();
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
        Map<String, String> normalizedHeaders = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null) {
                throw new IllegalArgumentException("Payment gateway webhook headers must have names and values");
            }
            String normalizedName = name.trim().toLowerCase(Locale.ROOT);
            if (normalizedHeaders.put(normalizedName, value) != null) {
                throw new IllegalArgumentException("Payment gateway webhook headers cannot repeat a name");
            }
        });
        return Map.copyOf(normalizedHeaders);
    }
}
