package com.renovar.canteiro.io.platform.subscription.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentGatewayChargeRequest(
        UUID subscriptionId,
        BigDecimal amount,
        LocalDate dueDate,
        PaymentGatewayBillingMethod billingMethod,
        String idempotencyKey
) {

    public PaymentGatewayChargeRequest {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("A payment gateway charge requires a subscription");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("A payment gateway charge amount must be greater than or equal to zero");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("A payment gateway charge amount must have at most two decimal places", exception);
        }
        if (dueDate == null || billingMethod == null) {
            throw new IllegalArgumentException("A payment gateway charge due date and billing method are required");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("A payment gateway charge idempotency key is required");
        }
        idempotencyKey = idempotencyKey.trim();
    }
}
