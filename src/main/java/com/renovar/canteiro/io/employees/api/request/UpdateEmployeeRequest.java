package com.renovar.canteiro.io.employees.api.request;

import com.renovar.canteiro.io.employees.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UpdateEmployeeRequest(@NotBlank @Size(max=255) String fullName, @Size(max=255) String preferredName,
 @NotBlank @Size(max=150) String jobTitle, @Size(max=150) String department, @Size(max=40) String phone,
 @NotNull EmployeeDocumentType documentType, @NotBlank @Size(max=64) String documentNumber, @Size(max=20) String pis,
 @NotNull LocalDate birthDate, @NotNull EmployeeEmploymentType employmentType, @NotNull LocalDate employmentStartDate,
 LocalDate employmentEndDate, @NotNull Boolean currentlyEmployed, @NotNull EmployeeGender gender, EmployeeEthnicity ethnicity,
 @NotBlank @Pattern(regexp="^(?:0|[1-9]\\d*)\\.\\d{2}$")
 @Schema(type = "string", format = "decimal", example = "3500.00", pattern = "^(?:0|[1-9]\\d*)\\.\\d{2}$") String compensationAmount,
 @NotNull EmployeeCompensationFrequency compensationFrequency, @Size(max=1000) String justification) { }
