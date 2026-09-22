package com.renovar.canteiro.io.employees.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface EmployeeJpaRepository extends JpaRepository<EmployeeJpaEntity, UUID>, JpaSpecificationExecutor<EmployeeJpaEntity> {
    Optional<EmployeeJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<EmployeeJpaEntity> findByUserIdAndCompanyId(UUID userId, UUID companyId);
}
