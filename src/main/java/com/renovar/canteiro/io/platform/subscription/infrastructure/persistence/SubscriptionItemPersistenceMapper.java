package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItem;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionItemPersistenceMapper {

    public SubscriptionItemJpaEntity toJpaEntity(SubscriptionItem subscriptionItem) {
        return new SubscriptionItemJpaEntity(
                subscriptionItem.getSubscriptionId(),
                subscriptionItem.getPlanId(),
                subscriptionItem.getPlanCode(),
                subscriptionItem.getPlanName()
        );
    }

    public SubscriptionItem toDomain(SubscriptionItemJpaEntity entity) {
        return SubscriptionItem.rehydrate(
                entity.getId(),
                entity.getSubscriptionId(),
                entity.getPlanId(),
                entity.getPlanCode(),
                entity.getPlanName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
