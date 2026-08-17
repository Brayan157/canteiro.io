package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlock;
import org.springframework.stereotype.Component;

@Component
public class TrustUnlockPersistenceMapper {

    public TrustUnlockJpaEntity toJpaEntity(TrustUnlock trustUnlock) {
        return new TrustUnlockJpaEntity(
                trustUnlock.getCompanyId(), trustUnlock.getChargeId(), trustUnlock.getGrantedByUserId(),
                trustUnlock.getReason(), trustUnlock.getStartsAt(), trustUnlock.getExpiresAt()
        );
    }

    public TrustUnlock toDomain(TrustUnlockJpaEntity entity) {
        return TrustUnlock.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getChargeId(), entity.getGrantedByUserId(),
                entity.getReason(), entity.getStartsAt(), entity.getExpiresAt(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
