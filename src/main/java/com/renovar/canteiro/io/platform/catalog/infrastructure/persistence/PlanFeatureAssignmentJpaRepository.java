package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PlanFeatureAssignmentJpaRepository extends JpaRepository<PlanFeatureAssignmentJpaEntity, UUID> {

    List<PlanFeatureAssignmentJpaEntity> findByPlanIdAndActiveTrue(UUID planId);
}
