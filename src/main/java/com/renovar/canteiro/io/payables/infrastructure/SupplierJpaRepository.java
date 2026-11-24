package com.renovar.canteiro.io.payables.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

interface SupplierJpaRepository extends JpaRepository<SupplierJpaEntity,UUID>,JpaSpecificationExecutor<SupplierJpaEntity> {
    Optional<SupplierJpaEntity> findByIdAndCompanyId(UUID id,UUID companyId);
}
