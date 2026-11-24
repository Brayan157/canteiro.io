package com.renovar.canteiro.io.payables.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

interface FinancialCategoryJpaRepository extends JpaRepository<FinancialCategoryJpaEntity,UUID>,JpaSpecificationExecutor<FinancialCategoryJpaEntity> {
    Optional<FinancialCategoryJpaEntity> findByIdAndCompanyId(UUID id,UUID companyId);
    boolean existsByCompanyIdAndCode(UUID companyId,String code);
}
