package com.renovar.canteiro.io.employees.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository {
    Employee save(Employee employee);
    Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId);
    Page<Employee> findByCompanyId(UUID companyId, Pageable pageable);
}
