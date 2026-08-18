package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "integrations.asaas", name = "enabled", havingValue = "true")
public class PaymentWebhookApplicationService {

    private final PaymentGateway paymentGateway;
    private final PaymentGatewayEventService eventService;
    public PaymentGatewayEvent receive(PaymentGatewayWebhookRequest request) {
        PaymentGatewayWebhook webhook = paymentGateway.verifyAndParseWebhook(request);
        return eventService.receiveVerifiedWebhook(paymentGateway.providerCode(), webhook, request.receivedAt());
    }
}
