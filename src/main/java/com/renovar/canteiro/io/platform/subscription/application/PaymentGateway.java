package com.renovar.canteiro.io.platform.subscription.application;

/**
 * Application port for external payment providers. Implementations authenticate
 * and translate provider-specific webhook payloads before they enter the domain.
 */
public interface PaymentGateway {

    PaymentGatewayChargeResult createCharge(PaymentGatewayChargeRequest request);

    PaymentGatewayWebhook verifyAndParseWebhook(PaymentGatewayWebhookRequest request);
}
