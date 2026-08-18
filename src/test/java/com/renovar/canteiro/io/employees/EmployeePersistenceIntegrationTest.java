package com.renovar.canteiro.io.employees;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
                firstCompany.getId(), "Ana da Silva", "Montadora", "11999999999"
        ));

        assertEquals("Ana da Silva", employeeRepository.findByIdAndCompanyId(employee.getId(), firstCompany.getId())
                .orElseThrow().getFullName());
        assertTrue(employeeRepository.findByIdAndCompanyId(employee.getId(), secondCompany.getId()).isEmpty());
        assertTrue(employee.getUserId() == null);
    }
}
