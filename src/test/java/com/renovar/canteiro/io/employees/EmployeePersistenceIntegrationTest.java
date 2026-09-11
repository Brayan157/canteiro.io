package com.renovar.canteiro.io.employees;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;
import com.renovar.canteiro.io.employees.domain.EmployeeDocumentType;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.employees.domain.EmployeeGender;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeePersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void persistsEmployeeWithoutUserAndPreventsCrossTenantLookup() {
        Company firstCompany = companyRepository.save(Company.create(
                "First company", null, "51000000000100", "first-employee@example.com", null, null, null
        ));
        Company secondCompany = companyRepository.save(Company.create(
                "Second company", null, "52000000000100", "second-employee@example.com", null, null, null
        ));

        Employee employee = employeeRepository.save(Employee.create(
                firstCompany.getId(), "Ana da Silva", null, "Montadora", null, "11999999999", "FIR-001",
                EmployeeDocumentType.CPF, "529.982.247-25", null, LocalDate.of(1990, 5, 20),
                EmployeeEmploymentType.EMPLOYEE, LocalDate.of(2025, 2, 3), null, true,
                EmployeeGender.FEMALE, null, EmployeeCompensationFrequency.MONTHLY, new BigDecimal("3500.00")
        ));

        assertEquals("Ana da Silva", employeeRepository.findByIdAndCompanyId(employee.getId(), firstCompany.getId())
                .orElseThrow().getFullName());
        assertTrue(employeeRepository.findByIdAndCompanyId(employee.getId(), secondCompany.getId()).isEmpty());
        assertTrue(employee.getUserId() == null);
    }
}
