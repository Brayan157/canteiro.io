package com.renovar.canteiro.io.employees.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.renovar.canteiro.io.employees.application.EmployeeFilter;

import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository {
    Employee save(Employee employee);
    Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<Employee> findByUserIdAndCompanyId(UUID userId, UUID companyId);
    Page<Employee> findByCompanyId(UUID companyId, EmployeeFilter filter, Pageable pageable);

    default Page<Employee> findByCompanyId(UUID companyId, Pageable pageable) {
        return findByCompanyId(companyId,
                new EmployeeFilter(null, null, null, null, null, null, null, null, null, null), pageable);
    }
}
