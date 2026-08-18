package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventStatus;
import java.time.Instant;
import java.util.List;

import java.util.Optional;
import java.util.UUID;

interface PaymentGatewayEventJpaRepository extends JpaRepository<PaymentGatewayEventJpaEntity, UUID> {

    Optional<PaymentGatewayEventJpaEntity> findByProviderAndExternalEventId(
            String provider,
            String externalEventId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT event FROM PaymentGatewayEventJpaEntity event WHERE event.id = :id")
    Optional<PaymentGatewayEventJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT event FROM PaymentGatewayEventJpaEntity event
            WHERE event.status = :received
               OR (event.status = :failed AND event.processedAt < :retryBefore)
            ORDER BY event.receivedAt ASC
            """)
    List<PaymentGatewayEventJpaEntity> findRetryable(
            @Param("received") PaymentGatewayEventStatus received,
            @Param("failed") PaymentGatewayEventStatus failed,
            @Param("retryBefore") Instant retryBefore,
            Pageable pageable
    );
}
