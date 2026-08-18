package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlock;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaTrustUnlockRepository implements TrustUnlockRepository {

    private final TrustUnlockJpaRepository repository;
    private final TrustUnlockPersistenceMapper mapper;

    @Override
    public TrustUnlock save(TrustUnlock trustUnlock) {
        if (trustUnlock.getId() != null) {
            throw new IllegalStateException("Trust unlocks are immutable and can only be created");
        }
        return mapper.toDomain(repository.saveAndFlush(mapper.toJpaEntity(trustUnlock)));
    }

    @Override
    public long countByChargeId(UUID chargeId) {
        return repository.countByChargeId(chargeId);
    }

    @Override
    public Set<UUID> findActiveChargeIdsByCompanyId(UUID companyId, Instant instant) {
        return Set.copyOf(repository.findActiveChargeIdsByCompanyId(companyId, instant));
    }
}
