package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaSubscriptionRepository implements SubscriptionRepository {

    private final SubscriptionJpaRepository subscriptionJpaRepository;
    private final SubscriptionPersistenceMapper subscriptionPersistenceMapper;

    @Override
    public Subscription save(Subscription subscription) {
        if (subscription.getId() == null) {
            return subscriptionPersistenceMapper.toDomain(
                    subscriptionJpaRepository.save(subscriptionPersistenceMapper.toJpaEntity(subscription))
            );
        }
        SubscriptionJpaEntity entity = subscriptionJpaRepository.findById(subscription.getId())
                .orElseThrow(() -> new IllegalStateException("Subscription must exist before its lifecycle can change"));
        subscriptionPersistenceMapper.updateJpaEntity(entity, subscription);
        return subscriptionPersistenceMapper.toDomain(subscriptionJpaRepository.save(entity));
    }

    @Override
    public Optional<Subscription> findById(UUID id) {
        return subscriptionJpaRepository.findById(id).map(subscriptionPersistenceMapper::toDomain);
    }

    @Override
    public List<Subscription> findByCompanyId(UUID companyId) {
        return subscriptionJpaRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(subscriptionPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Subscription> findByStatus(SubscriptionStatus status) {
        return subscriptionJpaRepository.findByStatus(status).stream()
                .map(subscriptionPersistenceMapper::toDomain)
                .toList();
    }
}
