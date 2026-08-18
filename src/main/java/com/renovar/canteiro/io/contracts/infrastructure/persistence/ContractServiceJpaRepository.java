package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;

interface ContractServiceJpaRepository extends JpaRepository<ContractServiceJpaEntity, UUID> {

    List<ContractServiceJpaEntity> findByContractIdAndCompanyId(UUID contractId, UUID companyId);
}
