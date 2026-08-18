package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface CompanySubscriptionAccessJpaRepository extends JpaRepository<CompanySubscriptionAccessJpaEntity, UUID> {
}
