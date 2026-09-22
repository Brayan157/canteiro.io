package com.renovar.canteiro.io.works;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WorkAndContractApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private FinalCustomerRepository finalCustomerRepository;
    @Autowired private WorkRepository workRepository;
    @Autowired private ContractRepository contractRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private UserRoleRepository userRoleRepository;

    @Test
    void filtersAndUpdatesWorksAndContractsOnlyInsideTheAuthenticatedTenant() throws Exception {
        Company company = createCompany("works-contracts-api");
        Company otherCompany = createCompany("works-contracts-api-other");
        User manager = createUser(company, "work-contract-manager@example.com");
        User outsider = createUser(otherCompany, "work-contract-outsider@example.com");
        grant(manager, company, AccessModule.WORKS, AccessAction.READ, AccessAction.UPDATE_DIRECT);
        grant(manager, company, AccessModule.CONTRACTS, AccessAction.READ, AccessAction.UPDATE_DIRECT);
        grant(outsider, otherCompany, AccessModule.WORKS, AccessAction.READ);
        grant(outsider, otherCompany, AccessModule.CONTRACTS, AccessAction.READ);

        FinalCustomer customer = customer(company, "Cliente de Obras");
        FinalCustomer otherCustomer = customer(otherCompany, "Cliente Externo");
        Work work = workRepository.save(Work.create(company.getId(), customer.getId(), "Centro Logístico", "OBR-2026-001",
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE,
                java.time.LocalDate.of(2026, 1, 10), java.time.LocalDate.of(2026, 12, 20), null));
        workRepository.save(Work.create(company.getId(), customer.getId(), "Outra obra", "OBR-2026-002",
                WorkExecutionLocationType.PROVIDER_UNIT, null, WorkStatus.DRAFT, null, null, null));
        Work externalWork = workRepository.save(Work.create(otherCompany.getId(), otherCustomer.getId(), "Centro Logístico", "OBR-EXT-001",
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE,
                java.time.LocalDate.of(2026, 1, 10), java.time.LocalDate.of(2026, 12, 20), null));
        Contract contract = contractRepository.save(Contract.create(company.getId(), work.getId(), "CTR-2026-001", "Contrato de fundação",
                ContractStatus.OPEN, java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 10, 31), null));
        contractRepository.save(Contract.create(company.getId(), work.getId(), "CTR-2026-002", "Contrato futuro",
                ContractStatus.DRAFT, null, null, null));
        Contract externalContract = contractRepository.save(Contract.create(otherCompany.getId(), externalWork.getId(), "CTR-EXT-001", "Contrato de fundação",
                ContractStatus.OPEN, java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 10, 31), null));

        mockMvc.perform(get("/api/v1/company/works")
                        .param("name", "logíst")
                        .param("reference", "obr-2026")
                        .param("finalCustomerId", customer.getId().toString())
                        .param("status", "ACTIVE")
                        .param("executionLocationType", "FINAL_CUSTOMER_LOCATION")
                        .param("startedFrom", "2026-01-01")
                        .param("startedTo", "2026-01-31")
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(work.getId().toString()));

        mockMvc.perform(get("/api/v1/company/works")
                        .param("name", "centro")
                        .param("finalCustomerName", "cliente de obras")
                        .param("finalCustomerDocument", customer.getDocument().substring(4, 12))
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(work.getId().toString()));

        mockMvc.perform(put("/api/v1/company/works/{workId}", work.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"name":"Centro Logístico Renovado","reference":"OBR-2026-001-R",
                                 "executionLocationType":"FINAL_CUSTOMER_LOCATION","status":"ACTIVE",
                                 "startedOn":"2026-01-10","expectedCompletionOn":"2026-12-20"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DIRECT"))
                .andExpect(jsonPath("$.work.name").value("Centro Logístico Renovado"))
                .andExpect(jsonPath("$.work.finalCustomerId").value(customer.getId().toString()));

        mockMvc.perform(get("/api/v1/company/contracts")
                        .param("name", "funda")
                        .param("reference", "ctr-2026")
                        .param("workId", work.getId().toString())
                        .param("status", "OPEN")
                        .param("startedFrom", "2026-02-01")
                        .param("startedTo", "2026-02-28")
                        .param("expectedCompletionFrom", "2026-10-01")
                        .param("expectedCompletionTo", "2026-10-31")
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(contract.getId().toString()));

        mockMvc.perform(get("/api/v1/company/contracts")
                        .param("name", "funda")
                        .param("workName", "centro logístico")
                        .param("finalCustomerName", "cliente de obras")
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(contract.getId().toString()));

        mockMvc.perform(put("/api/v1/company/contracts/{contractId}", contract.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"reference":"CTR-2026-001-R","name":"Contrato de fundação revisado","status":"ACTIVE",
                                 "startedOn":"2026-02-01","expectedCompletionOn":"2026-10-31"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DIRECT"))
                .andExpect(jsonPath("$.contract.name").value("Contrato de fundação revisado"))
                .andExpect(jsonPath("$.contract.workId").value(work.getId().toString()));

        mockMvc.perform(put("/api/v1/company/works/{workId}", externalWork.getId())
                        .with(jwt().jwt(token -> token.subject(outsider.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"name":"Tentativa externa","executionLocationType":"PROVIDER_UNIT","status":"ACTIVE"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/company/contracts/{contractId}", externalContract.getId())
                        .with(jwt().jwt(token -> token.subject(outsider.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"name":"Tentativa externa","status":"ACTIVE"}
                                """))
                .andExpect(status().isNotFound());
    }

    private FinalCustomer customer(Company company, String name) {
        String document = "12" + "%012d".formatted(Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),
                1_000_000_000_000L));
        return finalCustomerRepository.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL, name,
                document));
    }

    private void grant(User user, Company company, AccessModule module, AccessAction... actions) {
        Role role = roleRepository.save(Role.create(company.getId(), module + " role " + UUID.randomUUID(), null));
        for (AccessAction action : actions) {
            Permission permission = permissionRepository.findByModuleAndAction(module, action).orElseThrow();
            rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        }
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create("Company " + suffix, null,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.com", null, null, null));
    }

    private User createUser(Company company, String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash("Canteiro#2026Seguro"), Instant.now());
        User saved = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(saved.getId(), company.getId()));
        return saved;
    }
}
