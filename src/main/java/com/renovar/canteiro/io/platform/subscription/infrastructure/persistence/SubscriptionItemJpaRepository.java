package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SubscriptionItemJpaRepository extends JpaRepository<SubscriptionItemJpaEntity, UUID> {

    List<SubscriptionItemJpaEntity> findBySubscriptionIdOrderByPlanCode(UUID subscriptionId);
}
