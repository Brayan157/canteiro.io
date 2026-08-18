package com.renovar.canteiro.io.customers.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface FinalCustomerAddressJpaRepository extends JpaRepository<FinalCustomerAddressJpaEntity, UUID> {

    List<FinalCustomerAddressJpaEntity> findByFinalCustomerIdAndCompanyId(UUID finalCustomerId, UUID companyId);
}
