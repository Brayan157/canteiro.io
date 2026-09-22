package com.renovar.canteiro.io.customers.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

interface FinalCustomerJpaRepository extends JpaRepository<FinalCustomerJpaEntity, UUID>, JpaSpecificationExecutor<FinalCustomerJpaEntity> {

    Optional<FinalCustomerJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<FinalCustomerJpaEntity> findByCompanyIdAndDocument(UUID companyId, String document);

}
