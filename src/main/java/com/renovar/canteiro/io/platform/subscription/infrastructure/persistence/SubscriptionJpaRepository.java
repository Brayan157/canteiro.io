package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SubscriptionJpaRepository extends JpaRepository<SubscriptionJpaEntity, UUID> {

    List<SubscriptionJpaEntity> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<SubscriptionJpaEntity> findByStatus(SubscriptionStatus status);
}
