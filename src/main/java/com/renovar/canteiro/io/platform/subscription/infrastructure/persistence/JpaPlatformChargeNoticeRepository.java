package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlatformChargeNoticeRepository implements PlatformChargeNoticeRepository {

    private final PlatformChargeNoticeJpaRepository repository;
    private final PlatformChargeNoticePersistenceMapper mapper;
    private final EntityManager entityManager;

    @Override
    public boolean saveIfAbsent(PlatformChargeNotice notice) {
        return entityManager.createNativeQuery("""
                        INSERT INTO platform_charge_notice (
                            id, company_id, charge_id, notice_type, recipient_email, status, occurred_on, created_at, updated_at
                        ) VALUES (
                            :id, :companyId, :chargeId, :noticeType, :recipientEmail, :status, :occurredOn, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        ) ON CONFLICT (charge_id, notice_type) DO NOTHING
                        """)
                .setParameter("id", notice.getId())
                .setParameter("companyId", notice.getCompanyId())
                .setParameter("chargeId", notice.getChargeId())
                .setParameter("noticeType", notice.getNoticeType().name())
                .setParameter("recipientEmail", notice.getRecipientEmail())
                .setParameter("status", notice.getStatus().name())
                .setParameter("occurredOn", notice.getOccurredOn())
                .executeUpdate() == 1;
    }

    @Override
    public List<PlatformChargeNotice> findByChargeId(UUID chargeId) {
        return repository.findByChargeIdOrderByCreatedAtAsc(chargeId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<PlatformChargeNotice> claimPendingDeliveries(Instant attemptedAt, Instant staleBefore, int limit) {
        if (attemptedAt == null || staleBefore == null || limit < 1) {
            throw new IllegalArgumentException("Platform charge notice delivery claim is invalid");
        }
        List<PlatformChargeNoticeJpaEntity> notices = repository.findClaimableForUpdate(
                List.of(PlatformChargeNoticeStatus.PENDING_DELIVERY, PlatformChargeNoticeStatus.DELIVERY_FAILED),
                PlatformChargeNoticeStatus.DELIVERING,
                staleBefore,
                PageRequest.of(0, limit)
        );
        notices.forEach(notice -> notice.beginDelivery(attemptedAt));
        entityManager.flush();
        return notices.stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<PlatformChargeNotice> findByIdForUpdate(UUID noticeId) {
        return repository.findByIdForUpdate(noticeId).map(mapper::toDomain);
    }

    @Override
    public PlatformChargeNotice save(PlatformChargeNotice notice) {
        PlatformChargeNoticeJpaEntity entity = repository.findById(notice.getId())
                .orElseThrow(() -> new IllegalArgumentException("Platform charge notice was not found"));
        entity.applyDeliveryState(
                notice.getStatus(), notice.getDeliveryAttempts(), notice.getLastAttemptAt(), notice.getDeliveredAt(),
                notice.getFailureReason()
        );
        entityManager.flush();
        return mapper.toDomain(entity);
    }
}
