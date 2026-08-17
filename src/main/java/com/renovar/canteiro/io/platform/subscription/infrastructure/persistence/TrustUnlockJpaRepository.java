package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface TrustUnlockJpaRepository extends JpaRepository<TrustUnlockJpaEntity, UUID> {

    long countByChargeId(UUID chargeId);

    @Query("""
            SELECT DISTINCT trustUnlock.chargeId
            FROM TrustUnlockJpaEntity trustUnlock
            WHERE trustUnlock.companyId = :companyId
              AND trustUnlock.startsAt <= :instant
              AND trustUnlock.expiresAt > :instant
            """)
    List<UUID> findActiveChargeIdsByCompanyId(@Param("companyId") UUID companyId, @Param("instant") Instant instant);
}
