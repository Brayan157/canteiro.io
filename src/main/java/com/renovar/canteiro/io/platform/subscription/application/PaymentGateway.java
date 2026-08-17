package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;

/**
 * Application port for external payment providers. Implementations authenticate
 * and translate provider-specific webhook payloads before they enter the domain.
 */
public interface PaymentGateway {

    PaymentGatewayProviderCode providerCode();

    PaymentGatewayCustomerResult createCustomer(PaymentGatewayCustomerRequest request);

    PaymentGatewayChargeResult createCharge(PaymentGatewayChargeRequest request);

    PaymentGatewayChargeStatus findChargeStatus(String externalChargeId);

    PaymentGatewayWebhook verifyAndParseWebhook(PaymentGatewayWebhookRequest request);
}
