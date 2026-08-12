package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PlanBundleJpaRepository extends JpaRepository<PlanBundleJpaEntity, UUID> {

    Optional<PlanBundleJpaEntity> findByCode(String code);

    List<PlanBundleJpaEntity> findByActiveTrue();
}
