package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PlanPriceJpaRepository extends JpaRepository<PlanPriceJpaEntity, UUID> {

    List<PlanPriceJpaEntity> findByPlanIdOrderByValidFromAsc(UUID planId);
}
