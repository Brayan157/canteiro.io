package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentGatewayEventRepository {

    void lockExternalEventId(PaymentGatewayProviderCode provider, String externalEventId);

    PaymentGatewayEvent save(PaymentGatewayEvent event);

    Optional<PaymentGatewayEvent> findByProviderAndExternalEventId(
            PaymentGatewayProviderCode provider,
            String externalEventId
    );

    Optional<PaymentGatewayEvent> findByIdForUpdate(UUID id);

    List<PaymentGatewayEvent> findRetryable(Instant retryBefore, int limit);
}
