package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlatformChargeNoticeJpaRepository extends JpaRepository<PlatformChargeNoticeJpaEntity, UUID> {

    List<PlatformChargeNoticeJpaEntity> findByChargeIdOrderByCreatedAtAsc(UUID chargeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT notice FROM PlatformChargeNoticeJpaEntity notice
            WHERE notice.status IN :retryableStatuses
               OR (notice.status = :deliveringStatus AND notice.lastAttemptAt < :staleBefore)
            ORDER BY notice.createdAt ASC
            """)
    List<PlatformChargeNoticeJpaEntity> findClaimableForUpdate(
            @Param("retryableStatuses") List<PlatformChargeNoticeStatus> retryableStatuses,
            @Param("deliveringStatus") PlatformChargeNoticeStatus deliveringStatus,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT notice FROM PlatformChargeNoticeJpaEntity notice WHERE notice.id = :noticeId")
    Optional<PlatformChargeNoticeJpaEntity> findByIdForUpdate(@Param("noticeId") UUID noticeId);
}
