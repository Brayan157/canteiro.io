package com.renovar.canteiro.io.customers.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface FinalCustomerContactJpaRepository extends JpaRepository<FinalCustomerContactJpaEntity, UUID> {

    List<FinalCustomerContactJpaEntity> findByFinalCustomerIdAndCompanyId(UUID finalCustomerId, UUID companyId);
}
