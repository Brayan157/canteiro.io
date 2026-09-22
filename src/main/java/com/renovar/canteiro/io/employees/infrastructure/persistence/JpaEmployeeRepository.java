package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.employees.application.EmployeeFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.domain.Specification;

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
    public Optional<Employee> findByUserIdAndCompanyId(UUID userId, UUID companyId) {
        return jpaRepository.findByUserIdAndCompanyId(userId, companyId).map(mapper::toDomain);
    }

    @Override
    public Page<Employee> findByCompanyId(UUID companyId, EmployeeFilter filter, Pageable pageable) {
        return jpaRepository.findAll(specification(companyId, filter), pageable).map(mapper::toDomain);
    }

    private Specification<EmployeeJpaEntity> specification(UUID companyId, EmployeeFilter filter) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.equal(root.get("companyId"), companyId);
            if (filter.normalizedName() != null) {
                String name = like(filter.normalizedName());
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), name),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("preferredName")), name)
                ));
            }
            if (filter.normalizedDocument() != null) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.like(root.get("documentNumber"), "%" + filter.normalizedDocument() + "%"));
            }
            if (filter.normalizedJobTitle() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("jobTitle")), like(filter.normalizedJobTitle())));
            }
            if (filter.normalizedDepartment() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("department")), like(filter.normalizedDepartment())));
            }
            if (filter.employmentType() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("employmentType"), filter.employmentType()));
            }
            if (filter.compensationFrequency() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("compensationFrequency"), filter.compensationFrequency()));
            }
            if (filter.compensationMin() != null) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.greaterThanOrEqualTo(root.get("compensationAmount"), filter.compensationMin()));
            }
            if (filter.compensationMax() != null) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.lessThanOrEqualTo(root.get("compensationAmount"), filter.compensationMax()));
            }
            if (filter.currentlyEmployed() != null) {
                predicate = criteriaBuilder.and(predicate,
                        criteriaBuilder.equal(root.get("currentlyEmployed"), filter.currentlyEmployed()));
            }
            if (filter.active() != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("active"), filter.active()));
            }
            return predicate;
        };
    }

    private String like(String value) {
        return "%" + value.toLowerCase(java.util.Locale.ROOT) + "%";
    }
}
