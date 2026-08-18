package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItem;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaSubscriptionItemRepository implements SubscriptionItemRepository {

    private final SubscriptionItemJpaRepository subscriptionItemJpaRepository;
    private final SubscriptionItemPersistenceMapper subscriptionItemPersistenceMapper;

    @Override
    public SubscriptionItem save(SubscriptionItem subscriptionItem) {
        if (subscriptionItem.getId() != null) {
            throw new IllegalStateException("A subscription item snapshot cannot be replaced");
        }
        return subscriptionItemPersistenceMapper.toDomain(
                subscriptionItemJpaRepository.save(subscriptionItemPersistenceMapper.toJpaEntity(subscriptionItem))
        );
    }

    @Override
    public List<SubscriptionItem> findBySubscriptionId(UUID subscriptionId) {
        return subscriptionItemJpaRepository.findBySubscriptionIdOrderByPlanCode(subscriptionId).stream()
                .map(subscriptionItemPersistenceMapper::toDomain)
                .toList();
    }
}
