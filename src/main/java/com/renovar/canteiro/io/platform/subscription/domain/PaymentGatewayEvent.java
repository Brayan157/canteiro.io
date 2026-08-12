package com.renovar.canteiro.io.platform.subscription.domain;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhook;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookEventType;
import lombok.Getter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public final class PaymentGatewayEvent {

    private final UUID id;
    private final PaymentGatewayProviderCode provider;
    private final String externalEventId;
    private final String externalChargeId;
    private final PaymentGatewayWebhookEventType eventType;
    private final Instant occurredAt;
    private final Instant receivedAt;
    private final Map<String, String> attributes;
    private final PaymentGatewayEventStatus status;
    private final Instant processedAt;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PaymentGatewayEvent(
            UUID id,
            PaymentGatewayProviderCode provider,
            String externalEventId,
            String externalChargeId,
            PaymentGatewayWebhookEventType eventType,
            Instant occurredAt,
            Instant receivedAt,
            Map<String, String> attributes,
            PaymentGatewayEventStatus status,
            Instant processedAt,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.provider = require(provider, "Payment gateway event provider is required");
        this.externalEventId = requireText(externalEventId, "Payment gateway external event id is required");
        this.externalChargeId = requireText(externalChargeId, "Payment gateway external charge id is required");
        this.eventType = require(eventType, "Payment gateway event type is required");
        this.occurredAt = require(occurredAt, "Payment gateway event occurrence time is required");
        this.receivedAt = require(receivedAt, "Payment gateway event receipt time is required");
        this.attributes = Map.copyOf(new LinkedHashMap<>(require(attributes, "Payment gateway event attributes are required")));
        this.status = require(status, "Payment gateway event status is required");
        this.processedAt = processedAt;
        this.failureReason = normalizeOptional(failureReason);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PaymentGatewayEvent receive(
            PaymentGatewayProviderCode provider,
            PaymentGatewayWebhook webhook,
            Instant receivedAt
    ) {
        if (webhook == null) {
            throw new IllegalArgumentException("A verified payment gateway webhook is required");
        }
        return new PaymentGatewayEvent(
                null, provider, webhook.externalEventId(), webhook.externalChargeId(), webhook.eventType(),
                webhook.occurredAt(), receivedAt, webhook.attributes(), PaymentGatewayEventStatus.RECEIVED,
                null, null, null, null
        );
    }

    public static PaymentGatewayEvent rehydrate(
            UUID id,
            PaymentGatewayProviderCode provider,
            String externalEventId,
            String externalChargeId,
            PaymentGatewayWebhookEventType eventType,
            Instant occurredAt,
            Instant receivedAt,
            Map<String, String> attributes,
            PaymentGatewayEventStatus status,
            Instant processedAt,
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PaymentGatewayEvent(
                id, provider, externalEventId, externalChargeId, eventType, occurredAt, receivedAt,
                attributes, status, processedAt, failureReason, createdAt, updatedAt
        );
    }

    public boolean matches(PaymentGatewayWebhook webhook) {
        return externalChargeId.equals(webhook.externalChargeId())
                && eventType == webhook.eventType()
                && occurredAt.equals(webhook.occurredAt())
                && attributes.equals(webhook.attributes());
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

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
