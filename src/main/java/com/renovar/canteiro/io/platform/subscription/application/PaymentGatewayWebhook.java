package com.renovar.canteiro.io.platform.subscription.application;

import java.time.Instant;
import java.util.Map;

/**
 * A verified, provider-neutral webhook. The unverified raw request must never
 * be persisted or applied as a payment event before this translation succeeds.
 */
public record PaymentGatewayWebhook(
        String externalEventId,
        String externalChargeId,
        PaymentGatewayWebhookEventType eventType,
        Instant occurredAt,
        Map<String, String> attributes
) {

    public PaymentGatewayWebhook {
        externalEventId = requireText(externalEventId, "A payment gateway webhook external event id is required");
        externalChargeId = requireText(externalChargeId, "A payment gateway webhook external charge id is required");
        if (eventType == null || occurredAt == null || attributes == null) {
            throw new IllegalArgumentException("A payment gateway webhook event type, occurrence time, and attributes are required");
        }
        attributes = Map.copyOf(attributes);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
