package com.renovar.canteiro.io.employees;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency;
import com.renovar.canteiro.io.employees.domain.EmployeeDocumentType;
import com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType;
import com.renovar.canteiro.io.employees.domain.EmployeeGender;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.employees.application.EmployeeFilter;
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

    @Test
    void filtersEmployeesByNameDocumentJobTitleAndCompensationInsideTheirTenant() {
        Company company = companyRepository.save(Company.create(
                "Filter company", null, "53000000000100", "filter-employee@example.com", null, null, null
        ));
        Company otherCompany = companyRepository.save(Company.create(
                "Other filter company", null, "54000000000100", "filter-employee-other@example.com", null, null, null
        ));
        Employee matching = employeeRepository.save(Employee.create(
                company.getId(), "Marina Souza", "Mari", "Encarregada", "Obras", null, "FIL-001",
                EmployeeDocumentType.CPF, "529.982.247-25", null, LocalDate.of(1990, 5, 20),
                EmployeeEmploymentType.EMPLOYEE, LocalDate.of(2025, 2, 3), null, true,
                EmployeeGender.FEMALE, null, EmployeeCompensationFrequency.MONTHLY, new BigDecimal("4500.00")
        ));
        employeeRepository.save(Employee.create(
                company.getId(), "João Lima", null, "Montador", "Obras", null, "FIL-002",
                EmployeeDocumentType.CPF, "111.222.333-44", null, LocalDate.of(1990, 5, 20),
                EmployeeEmploymentType.EMPLOYEE, LocalDate.of(2025, 2, 3), null, true,
                EmployeeGender.MALE, null, EmployeeCompensationFrequency.MONTHLY, new BigDecimal("2500.00")
        ));
        employeeRepository.save(Employee.create(
                otherCompany.getId(), "Marina Souza", null, "Encarregada", "Obras", null, "OTH-001",
                EmployeeDocumentType.CPF, "529.982.247-25", null, LocalDate.of(1990, 5, 20),
                EmployeeEmploymentType.EMPLOYEE, LocalDate.of(2025, 2, 3), null, true,
                EmployeeGender.FEMALE, null, EmployeeCompensationFrequency.MONTHLY, new BigDecimal("4500.00")
        ));

        var result = employeeRepository.findByCompanyId(company.getId(), new EmployeeFilter(
                "mari", "529.982", "encarreg", "obras", EmployeeEmploymentType.EMPLOYEE,
                EmployeeCompensationFrequency.MONTHLY, new BigDecimal("4000.00"), new BigDecimal("5000.00"), true, true
        ), org.springframework.data.domain.Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        assertEquals(matching.getId(), result.getContent().getFirst().getId());
    }
}
