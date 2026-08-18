package com.renovar.canteiro.io.platform.subscription.application;

public enum PaymentGatewayWebhookEventType {
    CHARGE_CREATED,
    CHARGE_CONFIRMED,
    CHARGE_OVERDUE,
    CHARGE_CANCELLED
}
