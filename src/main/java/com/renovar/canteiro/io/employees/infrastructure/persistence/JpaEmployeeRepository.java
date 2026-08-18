package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaEmployeeRepository implements EmployeeRepository {
    private final EmployeeJpaRepository jpaRepository;
    private final EmployeePersistenceMapper mapper;

    @Override
    public Employee save(Employee employee) {
        if (employee.getId() == null) {
            return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(employee)));
        }
        EmployeeJpaEntity entity = jpaRepository.findById(employee.getId())
                .orElseThrow(() -> new IllegalStateException("Employee must exist before it can be updated"));
        mapper.updateJpaEntity(entity, employee);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toDomain);
    }

    @Override
    public Page<Employee> findByCompanyId(UUID companyId, Pageable pageable) {
        return jpaRepository.findByCompanyId(companyId, pageable).map(mapper::toDomain);
    }
}
