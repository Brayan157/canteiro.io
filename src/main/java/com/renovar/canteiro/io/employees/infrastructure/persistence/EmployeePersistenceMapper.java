package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.employees.domain.Employee;
import org.springframework.stereotype.Component;

@Component
class EmployeePersistenceMapper {
    EmployeeJpaEntity toJpaEntity(Employee employee) {
        return new EmployeeJpaEntity(employee.getCompanyId(), employee.getFullName(), employee.getJobTitle(),
                employee.getPhone(), employee.isActive(), employee.getUserId());
    }

    void updateJpaEntity(EmployeeJpaEntity entity, Employee employee) {
        if (employee.getUserId() != null && entity.getUserId() == null) {
            entity.linkUser(employee.getUserId());
        }
    }

    Employee toDomain(EmployeeJpaEntity entity) {
        return Employee.rehydrate(entity.getId(), entity.getCompanyId(), entity.getFullName(), entity.getJobTitle(),
                entity.getPhone(), entity.isActive(), entity.getUserId(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
