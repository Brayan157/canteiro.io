package com.renovar.canteiro.io.platform.subscription.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("subscription.payment-synchronization")
public record PaymentSynchronizationProperties(int batchSize, Duration retryAfter) {

    public PaymentSynchronizationProperties {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Payment synchronization batch size must be between 1 and 500");
        }
        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
            throw new IllegalArgumentException("Payment synchronization retry interval must be positive");
        }
    }
}
