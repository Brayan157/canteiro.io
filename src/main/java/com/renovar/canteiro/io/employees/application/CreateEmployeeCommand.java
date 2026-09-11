package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.employees.domain.EmployeeDocumentType;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.employees.domain.EmployeeGender;
import com.renovar.canteiro.io.employees.domain.EmployeeEthnicity;
import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;

import java.time.LocalDate;

public record CreateEmployeeCommand(
        String fullName,
        String preferredName,
        String jobTitle,
        String department,
        String phone,
        String registrationNumber,
        EmployeeDocumentType documentType,
        String documentNumber,
        String pis,
        LocalDate birthDate,
        EmployeeEmploymentType employmentType,
        LocalDate employmentStartDate,
        LocalDate employmentEndDate,
        boolean currentlyEmployed,
        EmployeeGender gender,
        EmployeeEthnicity ethnicity,
        String compensationAmount,
        EmployeeCompensationFrequency compensationFrequency,
        String justification
) {
}
