package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PaymentGatewayEventJpaRepository extends JpaRepository<PaymentGatewayEventJpaEntity, UUID> {

    Optional<PaymentGatewayEventJpaEntity> findByProviderAndExternalEventId(
            String provider,
            String externalEventId
    );
}
