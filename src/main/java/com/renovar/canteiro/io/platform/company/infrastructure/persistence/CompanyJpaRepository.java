package com.renovar.canteiro.io.platform.company.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface CompanyJpaRepository extends JpaRepository<CompanyJpaEntity, UUID> {

    Optional<CompanyJpaEntity> findByDocument(String document);

    Optional<CompanyJpaEntity> findByEmailIgnoreCase(String email);
}
