package com.renovar.canteiro.io.platform.subscription.application;

public record PaymentGatewayCustomerResult(String externalCustomerId) {

    public PaymentGatewayCustomerResult {
        if (externalCustomerId == null || externalCustomerId.isBlank()) {
            throw new IllegalArgumentException("A payment gateway customer result requires an external id");
        }
        externalCustomerId = externalCustomerId.trim();
    }
}
