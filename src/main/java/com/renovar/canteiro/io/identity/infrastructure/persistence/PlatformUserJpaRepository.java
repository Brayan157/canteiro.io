package com.renovar.canteiro.io.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PlatformUserJpaRepository extends JpaRepository<PlatformUserJpaEntity, UUID> {

    Optional<PlatformUserJpaEntity> findByUserId(UUID userId);
}
