package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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
}
