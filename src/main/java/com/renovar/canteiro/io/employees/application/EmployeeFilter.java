package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

/** Filters accepted by the employee list; monetary bounds are inclusive. */
public record EmployeeFilter(
        String name,
        String document,
        String jobTitle,
        String department,
        EmployeeEmploymentType employmentType,
        EmployeeCompensationFrequency compensationFrequency,
        BigDecimal compensationMin,
        BigDecimal compensationMax,
        Boolean currentlyEmployed,
        Boolean active
) {
    public EmployeeFilter {
        if (compensationMin != null && compensationMin.signum() < 0) {
            throw invalid("compensationMin must be zero or greater");
        }
        if (compensationMax != null && compensationMax.signum() < 0) {
            throw invalid("compensationMax must be zero or greater");
        }
        if (compensationMin != null && compensationMax != null && compensationMin.compareTo(compensationMax) > 0) {
            throw invalid("compensationMin must not exceed compensationMax");
        }
    }

    public String normalizedName() { return normalizeText(name); }
    public String normalizedJobTitle() { return normalizeText(jobTitle); }
    public String normalizedDepartment() { return normalizeText(department); }

    public String normalizedDocument() {
        if (document == null || document.isBlank()) {
            return null;
        }
        String normalized = document.replaceAll("\\D", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException invalid(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER, detail);
    }
}
