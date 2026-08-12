package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.Optional;

public interface PaymentGatewayEventRepository {

    void lockExternalEventId(PaymentGatewayProviderCode provider, String externalEventId);

    PaymentGatewayEvent save(PaymentGatewayEvent event);

    Optional<PaymentGatewayEvent> findByProviderAndExternalEventId(
            PaymentGatewayProviderCode provider,
            String externalEventId
    );
}
