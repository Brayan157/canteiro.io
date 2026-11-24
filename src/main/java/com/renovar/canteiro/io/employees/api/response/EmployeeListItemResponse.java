package com.renovar.canteiro.io.employees.api.response;

import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Minimal operational employee list item. Does not include documents, PIS, phone, birth date, gender, ethnicity or compensation. The full profile has a separate response contract.")
public record EmployeeListItemResponse(
        UUID id,
        String fullName,
        String preferredName,
        String jobTitle,
        String department,
        String registrationNumber,
        EmployeeEmploymentType employmentType,
        boolean currentlyEmployed,
        boolean active,
        UUID userId,
        @Schema(description = "Authenticated photo endpoint; requires EMPLOYEES.READ and the same tenant") String photoUrl
) {
    public static EmployeeListItemResponse from(Employee employee) {
        return new EmployeeListItemResponse(employee.getId(), employee.getFullName(), employee.getPreferredName(),
                employee.getJobTitle(), employee.getDepartment(), employee.getRegistrationNumber(), employee.getEmploymentType(),
                employee.isCurrentlyEmployed(), employee.isActive(), employee.getUserId(),
                employee.getPhotoStorageKey() == null ? null : "/api/v1/company/employees/" + employee.getId() + "/photo");
    }
}
