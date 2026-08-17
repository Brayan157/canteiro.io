package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT charge FROM PlatformChargeJpaEntity charge WHERE charge.id = :id")
    Optional<PlatformChargeJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    List<PlatformChargeJpaEntity> findByCompanyIdAndStatusInOrderByDueDateAsc(
            UUID companyId,
            Collection<PlatformChargeStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT charge
            FROM PlatformChargeJpaEntity charge
            WHERE charge.companyId = :companyId
              AND charge.status IN :statuses
            ORDER BY charge.dueDate ASC
            """)
    List<PlatformChargeJpaEntity> findForDunningByCompanyIdAndStatusIn(
            @Param("companyId") UUID companyId,
            @Param("statuses") Collection<PlatformChargeStatus> statuses
    );

    List<PlatformChargeJpaEntity> findByStatusInOrderByDueDateAsc(
            Collection<PlatformChargeStatus> statuses
    );

    @Query("""
            SELECT DISTINCT charge.companyId
            FROM PlatformChargeJpaEntity charge
            WHERE charge.status IN :statuses
            """)
    List<UUID> findDistinctCompanyIdsByStatusIn(@Param("statuses") Collection<PlatformChargeStatus> statuses);
}
