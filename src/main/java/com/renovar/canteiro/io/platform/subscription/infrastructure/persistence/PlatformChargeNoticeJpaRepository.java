package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PlatformChargeNoticeJpaRepository extends JpaRepository<PlatformChargeNoticeJpaEntity, UUID> {

    List<PlatformChargeNoticeJpaEntity> findByChargeIdOrderByCreatedAtAsc(UUID chargeId);
}
