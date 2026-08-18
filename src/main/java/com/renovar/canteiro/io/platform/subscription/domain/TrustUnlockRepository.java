package com.renovar.canteiro.io.platform.subscription.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface TrustUnlockRepository {

    TrustUnlock save(TrustUnlock trustUnlock);

    long countByChargeId(UUID chargeId);

    Set<UUID> findActiveChargeIdsByCompanyId(UUID companyId, Instant instant);
}
