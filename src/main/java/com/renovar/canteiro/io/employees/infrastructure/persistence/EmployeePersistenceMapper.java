package com.renovar.canteiro.io.employees.infrastructure.persistence;

import com.renovar.canteiro.io.employees.domain.Employee;
import org.springframework.stereotype.Component;

@Component
class EmployeePersistenceMapper {
    EmployeeJpaEntity toJpaEntity(Employee employee) {
        return new EmployeeJpaEntity(employee.getCompanyId(), employee.getFullName(), employee.getPreferredName(),
                employee.getJobTitle(), employee.getDepartment(), employee.getPhone(), employee.getRegistrationNumber(),
                employee.getDocumentType(), employee.getDocumentNumber(), employee.getPis(), employee.getBirthDate(), employee.getEmploymentType(),
                employee.getEmploymentStartDate(), employee.getEmploymentEndDate(), employee.isCurrentlyEmployed(),
                employee.getGender(), employee.getEthnicity(), employee.getCompensationFrequency(), employee.getCompensationAmount(),
                employee.isActive(), employee.getPhotoStorageKey(), employee.getUserId());
    }

    void updateJpaEntity(EmployeeJpaEntity entity, Employee employee) {
        entity.updateProfile(toJpaEntity(employee));
        entity.updatePhotoStorageKey(employee.getPhotoStorageKey());
        if (employee.getUserId() != null && entity.getUserId() == null) {
            entity.linkUser(employee.getUserId());
        }
    }

    Employee toDomain(EmployeeJpaEntity entity) {
        return Employee.rehydrate(entity.getId(), entity.getCompanyId(), entity.getFullName(), entity.getPreferredName(),
                entity.getJobTitle(), entity.getDepartment(), entity.getPhone(), entity.getRegistrationNumber(),
                entity.getDocumentType(), entity.getDocumentNumber(), entity.getPis(), entity.getBirthDate(), entity.getEmploymentType(),
                entity.getEmploymentStartDate(), entity.getEmploymentEndDate(), entity.isCurrentlyEmployed(), entity.getGender(),
                entity.getEthnicity(), entity.getCompensationFrequency(), entity.getCompensationAmount(), entity.isActive(),
                entity.getPhotoStorageKey(), entity.getUserId(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
