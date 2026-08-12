package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlatformChargeJpaRepository extends JpaRepository<PlatformChargeJpaEntity, UUID> {

    Optional<PlatformChargeJpaEntity> findByProviderAndIdempotencyKey(
            String provider,
            String idempotencyKey
    );

    Optional<PlatformChargeJpaEntity> findByProviderAndExternalChargeId(
            String provider,
            String externalChargeId
    );
}
