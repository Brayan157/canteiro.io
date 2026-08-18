package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ContractJpaRepository extends JpaRepository<ContractJpaEntity, UUID> {

    Optional<ContractJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
