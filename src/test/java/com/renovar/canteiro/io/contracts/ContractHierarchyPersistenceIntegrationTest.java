package com.renovar.canteiro.io.contracts;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.ServiceTemplate;
import com.renovar.canteiro.io.contracts.domain.ServiceTemplateRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContractHierarchyPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private FinalCustomerRepository finalCustomerRepository;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private ServiceTemplateRepository serviceTemplateRepository;

    @Autowired
    private ContractServiceRepository contractServiceRepository;

    @Test
    void persistsTheCompleteTenantBoundHierarchyAndStatusWithoutPhysicalDeletion() {
        Company company = createCompany("hierarchy");
        FinalCustomer customer = finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.LEGAL, "Customer", "12345678000190"
        ));
        Work work = workRepository.save(Work.create(
                company.getId(), customer.getId(), "Plant expansion", null,
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE,
                null, null, null
        ));
        Contract contract = contractRepository.save(Contract.create(
                company.getId(), work.getId(), null, "Assembly contract", ContractStatus.OPEN, null, null, null
        ));
        ServiceTemplate template = serviceTemplateRepository.save(ServiceTemplate.create(
                company.getId(), "Assembly", "Assembly service"
        ));
        ContractService service = contractServiceRepository.save(ContractService.create(
                company.getId(), contract.getId(), "Assembly", "Assembly service", ContractServiceStatus.CANCELLED,
                java.math.BigDecimal.ONE, java.math.BigDecimal.ZERO
        ));

        customer.deactivate();
        finalCustomerRepository.save(customer);

        assertEquals(customer.getId(), finalCustomerRepository
                .findByIdAndCompanyId(customer.getId(), company.getId()).orElseThrow().getId());
        assertFalse(finalCustomerRepository
                .findByIdAndCompanyId(customer.getId(), company.getId()).orElseThrow().isActive());
        assertEquals(WorkStatus.ACTIVE, workRepository.findByIdAndCompanyId(work.getId(), company.getId()).orElseThrow().getStatus());
        assertEquals(ContractStatus.OPEN, contractRepository.findByIdAndCompanyId(contract.getId(), company.getId()).orElseThrow().getStatus());
        assertEquals(ContractServiceStatus.CANCELLED, service.getStatus());
        assertEquals(template.getId(), serviceTemplateRepository.findByIdAndCompanyId(template.getId(), company.getId())
                .orElseThrow().getId());
    }

    @Test
    void rejectsAWorkReferencingAFinalCustomerFromAnotherTenant() {
        Company firstCompany = createCompany("first");
        Company secondCompany = createCompany("second");
        FinalCustomer firstCustomer = finalCustomerRepository.save(FinalCustomer.create(
                firstCompany.getId(), FinalCustomerType.INDIVIDUAL, "Ana", "12345678901"
        ));

        assertThrows(DataIntegrityViolationException.class, () -> workRepository.save(Work.create(
                secondCompany.getId(), firstCustomer.getId(), "Cross tenant work", null,
                WorkExecutionLocationType.PROVIDER_UNIT, null, WorkStatus.DRAFT, null, null, null
        )));
    }

    private Company createCompany(String suffix) {
        String value = UUID.randomUUID().toString();
        return companyRepository.save(Company.create(
                "Company " + suffix + value, suffix, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                suffix + value + "@example.com", null, null, null
        ));
    }
}
