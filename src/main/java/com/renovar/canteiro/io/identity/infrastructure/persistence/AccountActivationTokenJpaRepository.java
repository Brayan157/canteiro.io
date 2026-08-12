package com.renovar.canteiro.io.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface AccountActivationTokenJpaRepository extends JpaRepository<AccountActivationTokenJpaEntity, UUID> {

    Optional<AccountActivationTokenJpaEntity> findByTokenHash(String tokenHash);
}
