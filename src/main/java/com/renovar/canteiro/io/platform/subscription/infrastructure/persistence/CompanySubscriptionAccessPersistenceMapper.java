package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccess;
import org.springframework.stereotype.Component;

@Component
public class CompanySubscriptionAccessPersistenceMapper {

    public CompanySubscriptionAccessJpaEntity toJpaEntity(CompanySubscriptionAccess access) {
        return new CompanySubscriptionAccessJpaEntity(
                access.getCompanyId(), access.getAccessLevel(), access.getRestrictionChargeId(), access.getEffectiveOn()
        );
    }

    public void updateJpaEntity(CompanySubscriptionAccessJpaEntity entity, CompanySubscriptionAccess access) {
        entity.update(access.getAccessLevel(), access.getRestrictionChargeId(), access.getEffectiveOn());
    }

    public CompanySubscriptionAccess toDomain(CompanySubscriptionAccessJpaEntity entity) {
        return CompanySubscriptionAccess.rehydrate(
                entity.getCompanyId(), entity.getAccessLevel(), entity.getRestrictionChargeId(), entity.getEffectiveOn(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
