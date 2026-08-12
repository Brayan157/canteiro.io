package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
        if (event.getId() != null) {
            throw new IllegalStateException("Payment gateway event processing updates are not implemented yet");
        }
        return mapper.toDomain(repository.saveAndFlush(mapper.toJpaEntity(event)));
    }

    @Override
    public Optional<PaymentGatewayEvent> findByProviderAndExternalEventId(
            PaymentGatewayProviderCode provider,
            String externalEventId
    ) {
        return repository.findByProviderAndExternalEventId(provider.value(), externalEventId).map(mapper::toDomain);
    }

    private void acquireTransactionLock(String lockKey) {
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))"
                )
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
