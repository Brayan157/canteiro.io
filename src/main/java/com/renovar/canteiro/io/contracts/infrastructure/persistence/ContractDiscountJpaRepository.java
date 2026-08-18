package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ContractDiscountJpaRepository extends JpaRepository<ContractDiscountJpaEntity, UUID> {

    Optional<ContractDiscountJpaEntity> findByContractIdAndCompanyId(UUID contractId, UUID companyId);
}
