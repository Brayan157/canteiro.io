package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPersistenceMapper {

    public SubscriptionJpaEntity toJpaEntity(Subscription subscription) {
        return new SubscriptionJpaEntity(
                subscription.getCompanyId(),
                subscription.getStatus(),
                subscription.getQuotedAmount(),
                subscription.getPricingSource(),
                subscription.getPlanBundleId(),
                subscription.getPricingEffectiveDate(),
                subscription.getTrialStartedOn(),
                subscription.getTrialEndsOn()
        );
    }

    public void updateJpaEntity(SubscriptionJpaEntity entity, Subscription subscription) {
        entity.updateLifecycle(subscription.getStatus(), subscription.getTrialStartedOn(), subscription.getTrialEndsOn());
    }

    public Subscription toDomain(SubscriptionJpaEntity entity) {
        return Subscription.rehydrate(
                entity.getId(),
                entity.getCompanyId(),
                entity.getStatus(),
                entity.getQuotedAmount(),
                entity.getPricingSource(),
                entity.getPlanBundleId(),
                entity.getPricingEffectiveDate(),
                entity.getTrialStartedOn(),
                entity.getTrialEndsOn(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
