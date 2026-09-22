package com.renovar.canteiro.io.employees.api.response;

import com.renovar.canteiro.io.employees.domain.EmployeeDocumentType;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.employees.domain.EmployeeGender;
import com.renovar.canteiro.io.employees.domain.EmployeeEthnicity;
import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "EmployeeResponse", description = "Operational employee record, independent from system access.")
public record EmployeeResponse(
        UUID id,
        String fullName,
        String preferredName,
        String jobTitle,
        String department,
        String phone,
        String registrationNumber,
        EmployeeDocumentType documentType,
        String documentNumber,
        String pisMasked,
        LocalDate birthDate,
        EmployeeEmploymentType employmentType,
        LocalDate employmentStartDate,
        LocalDate employmentEndDate,
        boolean currentlyEmployed,
        EmployeeGender gender,
        EmployeeEthnicity ethnicity,
        EmployeeCompensationFrequency compensationFrequency,
        @Schema(type = "string", format = "decimal", example = "3500.00") String compensationAmount,
        boolean active,
        UUID userId,
        @Schema(description = "Authenticated employee-photo download URL; null when no identification photo was registered") String photoUrl
) {
}
