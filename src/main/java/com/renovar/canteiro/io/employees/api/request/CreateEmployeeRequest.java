package com.renovar.canteiro.io.employees.api.request;

import com.renovar.canteiro.io.employees.domain.EmployeeDocumentType;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.employees.domain.EmployeeGender;
import com.renovar.canteiro.io.employees.domain.EmployeeEthnicity;
import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateEmployeeRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 255) String preferredName,
        @NotBlank @Size(max = 150) String jobTitle,
        @Size(max = 150) String department,
        @Size(max = 40) String phone,
        @Size(max = 50) String registrationNumber,
        @NotNull EmployeeDocumentType documentType,
        @NotBlank @Size(max = 64) String documentNumber,
        @Size(max = 20) String pis,
        @NotNull LocalDate birthDate,
        @jakarta.validation.constraints.NotNull EmployeeEmploymentType employmentType,
        @NotNull LocalDate employmentStartDate,
        LocalDate employmentEndDate,
        @jakarta.validation.constraints.NotNull Boolean currentlyEmployed,
        @jakarta.validation.constraints.NotNull EmployeeGender gender,
        EmployeeEthnicity ethnicity,
        @Pattern(regexp = "^(?:0|[1-9]\\d*)\\.\\d{2}$")
        @Schema(type = "string", format = "decimal", example = "3500.00", pattern = "^(?:0|[1-9]\\d*)\\.\\d{2}$")
        @jakarta.validation.constraints.NotBlank String compensationAmount,
        @jakarta.validation.constraints.NotNull EmployeeCompensationFrequency compensationFrequency,
        @Size(max = 1000) String justification
) {
}
