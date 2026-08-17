package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventStatus;

@Repository
@RequiredArgsConstructor
public class JpaPaymentGatewayEventRepository implements PaymentGatewayEventRepository {

    private final PaymentGatewayEventJpaRepository repository;
    private final PaymentGatewayEventPersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    public void lockExternalEventId(PaymentGatewayProviderCode provider, String externalEventId) {
        acquireTransactionLock(provider.value() + ":event:" + externalEventId);
    }

    @Override
    public PaymentGatewayEvent save(PaymentGatewayEvent event) {
        if (event.getId() == null) {
            return mapper.toDomain(repository.saveAndFlush(mapper.toJpaEntity(event)));
        }
        PaymentGatewayEventJpaEntity entity = repository.findById(event.getId())
                .orElseThrow(() -> new IllegalArgumentException("Payment gateway event does not exist"));
        entity.updateProcessing(event.getStatus(), event.getProcessedAt(), event.getFailureReason());
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<PaymentGatewayEvent> findByProviderAndExternalEventId(
            PaymentGatewayProviderCode provider,
            String externalEventId
    ) {
        return repository.findByProviderAndExternalEventId(provider.value(), externalEventId).map(mapper::toDomain);
    }

    @Override
    public Optional<PaymentGatewayEvent> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public List<PaymentGatewayEvent> findRetryable(Instant retryBefore, int limit) {
        return repository.findRetryable(
                PaymentGatewayEventStatus.RECEIVED, PaymentGatewayEventStatus.FAILED,
                retryBefore, PageRequest.of(0, limit)
        ).stream().map(mapper::toDomain).toList();
    }

    private void acquireTransactionLock(String lockKey) {
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))"
                )
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
