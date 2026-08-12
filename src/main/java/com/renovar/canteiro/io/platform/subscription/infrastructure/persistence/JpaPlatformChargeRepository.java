package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlatformChargeRepository implements PlatformChargeRepository {

    private final PlatformChargeJpaRepository repository;
    private final PlatformChargePersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    public void lockIdempotencyKey(PaymentGatewayProviderCode provider, String idempotencyKey) {
        acquireTransactionLock(provider.value() + ":charge:" + idempotencyKey);
    }

    @Override
    public PlatformCharge save(PlatformCharge charge) {
        if (charge.getId() != null) {
            throw new IllegalStateException("Platform charge lifecycle updates are not implemented yet");
        }
        return mapper.toDomain(repository.saveAndFlush(mapper.toJpaEntity(charge)));
    }

    @Override
    public Optional<PlatformCharge> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PlatformCharge> findByProviderAndIdempotencyKey(
            PaymentGatewayProviderCode provider,
            String idempotencyKey
    ) {
        return repository.findByProviderAndIdempotencyKey(provider.value(), idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public Optional<PlatformCharge> findByProviderAndExternalChargeId(
            PaymentGatewayProviderCode provider,
            String externalChargeId
    ) {
        return repository.findByProviderAndExternalChargeId(provider.value(), externalChargeId).map(mapper::toDomain);
    }

    private void acquireTransactionLock(String lockKey) {
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))"
                )
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }
}
