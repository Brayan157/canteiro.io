package com.renovar.canteiro.io.employees.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface EmployeeJpaRepository extends JpaRepository<EmployeeJpaEntity, UUID> {
    Optional<EmployeeJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    Page<EmployeeJpaEntity> findByCompanyId(UUID companyId, Pageable pageable);
}
