package com.renovar.canteiro.io.platform.subscription.application;

public record PaymentGatewayChargeResult(
        String externalChargeId,
        PaymentGatewayChargeStatus status
) {

    public PaymentGatewayChargeResult {
        if (externalChargeId == null || externalChargeId.isBlank() || status == null) {
            throw new IllegalArgumentException("A payment gateway charge result requires an external id and status");
        }
        externalChargeId = externalChargeId.trim();
    }
}
