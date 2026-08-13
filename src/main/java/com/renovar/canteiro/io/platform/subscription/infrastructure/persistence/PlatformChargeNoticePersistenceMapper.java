package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNotice;
import org.springframework.stereotype.Component;

@Component
public class PlatformChargeNoticePersistenceMapper {

    public PlatformChargeNoticeJpaEntity toJpaEntity(PlatformChargeNotice notice) {
        return new PlatformChargeNoticeJpaEntity(
                notice.getId(), notice.getCompanyId(), notice.getChargeId(), notice.getNoticeType(),
                notice.getRecipientEmail(), notice.getStatus(), notice.getOccurredOn()
        );
    }

    public PlatformChargeNotice toDomain(PlatformChargeNoticeJpaEntity entity) {
        return PlatformChargeNotice.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getChargeId(), entity.getNoticeType(),
                entity.getRecipientEmail(), entity.getStatus(), entity.getOccurredOn(), entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
