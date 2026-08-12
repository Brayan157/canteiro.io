package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlanJpaRepository extends JpaRepository<PlanJpaEntity, UUID> {

    Optional<PlanJpaEntity> findByCode(String code);
}
