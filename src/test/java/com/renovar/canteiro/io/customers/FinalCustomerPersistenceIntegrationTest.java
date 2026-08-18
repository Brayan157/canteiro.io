package com.renovar.canteiro.io.customers;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalCustomerPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private FinalCustomerRepository finalCustomerRepository;

    @Test
    void persistsFinalCustomersWithDocumentsUniqueInsideTheirTenant() {
        Company firstCompany = createCompany("10000000000100", "first@example.com");
        Company secondCompany = createCompany("20000000000100", "second@example.com");
        FinalCustomer firstCustomer = finalCustomerRepository.save(FinalCustomer.create(
                firstCompany.getId(), FinalCustomerType.LEGAL, "Final Customer", "12.345.678/0001-90"
        ));

        FinalCustomer sameDocumentInOtherTenant = finalCustomerRepository.save(FinalCustomer.create(
                secondCompany.getId(), FinalCustomerType.LEGAL, "Another Final Customer", "12.345.678/0001-90"
        ));

        assertEquals(firstCustomer.getId(), finalCustomerRepository
                .findByCompanyIdAndDocument(firstCompany.getId(), "12345678000190").orElseThrow().getId());
        assertTrue(finalCustomerRepository.findByIdAndCompanyId(firstCustomer.getId(), secondCompany.getId()).isEmpty());
        assertEquals(sameDocumentInOtherTenant.getId(), finalCustomerRepository
                .findByCompanyIdAndDocument(secondCompany.getId(), "12.345.678/0001-90").orElseThrow().getId());
    }

    @Test
    void preventsDuplicatingAFinalCustomerDocumentInsideTheSameTenant() {
        Company company = createCompany("30000000000100", "third@example.com");
        finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.INDIVIDUAL, "Ana Silva", "123.456.789-01"
        ));

        assertThrows(DataIntegrityViolationException.class, () -> finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.INDIVIDUAL, "Ana Souza", "12345678901"
        )));
    }

    private Company createCompany(String document, String email) {
        return companyRepository.save(Company.create(
                "Company " + document, null, document, email, null, null, null
        ));
    }
}
