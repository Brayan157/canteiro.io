package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.Optional;
import java.util.UUID;

public interface PlatformChargeRepository {

    void lockIdempotencyKey(PaymentGatewayProviderCode provider, String idempotencyKey);

    PlatformCharge save(PlatformCharge charge);

    Optional<PlatformCharge> findById(UUID id);

    Optional<PlatformCharge> findByProviderAndIdempotencyKey(
            PaymentGatewayProviderCode provider,
            String idempotencyKey
    );

    Optional<PlatformCharge> findByProviderAndExternalChargeId(
            PaymentGatewayProviderCode provider,
            String externalChargeId
    );
}
