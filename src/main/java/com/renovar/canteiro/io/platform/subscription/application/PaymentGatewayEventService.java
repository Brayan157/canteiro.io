package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaymentGatewayEventService {

    private final PaymentGatewayEventRepository repository;

    @Transactional
    public PaymentGatewayEvent receiveVerifiedWebhook(
            PaymentGatewayProviderCode provider,
            PaymentGatewayWebhook webhook,
            Instant receivedAt
    ) {
        repository.lockExternalEventId(provider, webhook.externalEventId());
        return repository.findByProviderAndExternalEventId(provider, webhook.externalEventId())
                .map(existing -> requireSameEvent(existing, webhook))
                .orElseGet(() -> repository.save(PaymentGatewayEvent.receive(provider, webhook, receivedAt)));
    }

    private PaymentGatewayEvent requireSameEvent(PaymentGatewayEvent event, PaymentGatewayWebhook webhook) {
        if (!event.matches(webhook)) {
            throw new IllegalArgumentException("External event id was already used for a different webhook");
        }
        return event;
    }
}
