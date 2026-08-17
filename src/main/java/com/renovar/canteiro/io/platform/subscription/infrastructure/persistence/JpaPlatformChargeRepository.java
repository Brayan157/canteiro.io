package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

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
        if (charge.getId() == null) {
            return mapper.toDomain(repository.saveAndFlush(mapper.toJpaEntity(charge)));
        }
        PlatformChargeJpaEntity entity = repository.findById(charge.getId())
                .orElseThrow(() -> new IllegalArgumentException("Platform charge does not exist"));
        entity.updateLifecycle(charge.getStatus(), charge.getLastGatewayEventAt());
        return mapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    public Optional<PlatformCharge> findById(UUID id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PlatformCharge> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(mapper::toDomain);
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

    @Override
    public Optional<PlatformCharge> findByProviderAndExternalChargeIdForUpdate(
            PaymentGatewayProviderCode provider, String externalChargeId
    ) {
        return repository.findByProviderAndExternalChargeIdForUpdate(provider.value(), externalChargeId)
                .map(mapper::toDomain);
    }

    @Override
    public List<PlatformCharge> findReconciliationCandidates(int limit) {
        return repository.findByStatusInOrderByUpdatedAtAsc(outstandingStatuses(), PageRequest.of(0, limit)).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public List<PlatformCharge> findOutstandingByCompanyId(UUID companyId) {
        return repository.findByCompanyIdAndStatusInOrderByDueDateAsc(companyId, outstandingStatuses()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<PlatformCharge> findOutstandingByCompanyIdForDunning(UUID companyId) {
        return repository.findForDunningByCompanyIdAndStatusIn(companyId, outstandingStatuses()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<PlatformCharge> findAllOutstanding() {
        return repository.findByStatusInOrderByDueDateAsc(outstandingStatuses()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<UUID> findCompanyIdsWithOutstandingCharges() {
        return repository.findDistinctCompanyIdsByStatusIn(outstandingStatuses());
    }

    private void acquireTransactionLock(String lockKey) {
        entityManager.createNativeQuery(
                        "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))"
                )
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }

    private Set<PlatformChargeStatus> outstandingStatuses() {
        return Set.of(
                PlatformChargeStatus.PENDING,
                PlatformChargeStatus.OVERDUE
        );
    }
}
