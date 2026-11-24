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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WorkAndContractApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
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

    @Autowired private com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository discounts;
    @Autowired private com.renovar.canteiro.io.contracts.domain.ContractServiceRepository services;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.renovar.canteiro.io.contracts.application.ContractMeasurementAdjustmentAmountProvider adjustments;

    @Test void operationalProfileUpdatePreservesServiceDiscountAndHeaderDiscountWithOmittedOrNullFields() throws Exception {
        for (boolean nullFields : java.util.List.of(false,true)) {
            var f=discountedService();
            discounts.save(com.renovar.canteiro.io.contracts.domain.ContractDiscount.create(f.company().getId(),f.contract().getId(),com.renovar.canteiro.io.contracts.domain.DiscountType.PERCENTAGE,new java.math.BigDecimal("5.0000")));
            String body=operationalServiceBody(nullFields ? ",\"discountType\":null,\"discountValue\":null" : "");
            mockMvc.perform(put(servicePath(f)).with(jwt().jwt(t->t.subject(f.user().getId().toString()))).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.service.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.service.unitPrice").value("300000.00"))
                    .andExpect(jsonPath("$.service.discountType").value("PERCENTAGE"))
                    .andExpect(jsonPath("$.service.discountValue").value("10.0000"))
                    .andExpect(jsonPath("$.service.discountAmount").value("30000.00"))
                    .andExpect(jsonPath("$.service.netAmount").value("270000.00"))
                    .andExpect(jsonPath("$.contract.serviceSubtotal").value("270000.00"))
                    .andExpect(jsonPath("$.contract.contractDiscountAmount").value("13500.00"))
                    .andExpect(jsonPath("$.contract.netAmount").value("256500.00"));
            var saved=services.findByIdAndCompanyId(f.service().getId(),f.company().getId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("270000.00"),saved.getNetAmount());
            org.junit.jupiter.api.Assertions.assertEquals(f.service().getQuantity(),saved.getQuantity());
            org.junit.jupiter.api.Assertions.assertEquals(f.service().getUnitPrice(),saved.getUnitPrice());
            mockMvc.perform(get("/api/v1/company/billing-control/contracts/"+f.contract().getId()).with(jwt().jwt(t->t.subject(f.user().getId().toString()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.contractNetAmount").value("256500.00"));
        }
    }

    @Test void approvalOfProfileUpdatePreservesTheDiscountCurrentAtApplicationRatherThanOldSnapshot() throws Exception {
        var f=discountedService();var requester=createUser(f.company(),UUID.randomUUID()+"@example.test");
        grant(requester,f.company(),AccessModule.SERVICES,AccessAction.REQUEST_UPDATE);
        var approver=createUser(f.company(),UUID.randomUUID()+"@example.test");grant(approver,f.company(),AccessModule.SERVICES,AccessAction.APPROVE);
        String response=mockMvc.perform(put(servicePath(f)).with(jwt().jwt(t->t.subject(requester.getId().toString()))).contentType("application/json").content(operationalServiceBody("")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(com.renovar.canteiro.io.contracts.domain.ContractServiceStatus.ACTIVE,services.findByIdAndCompanyId(f.service().getId(),f.company().getId()).orElseThrow().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("270000.00"),services.findByIdAndCompanyId(f.service().getId(),f.company().getId()).orElseThrow().getNetAmount());
        // A legitimate later discount exists when the profile proposal is finally applied.
        services.save(f.service().withDiscount(com.renovar.canteiro.io.contracts.domain.DiscountType.FIXED,new java.math.BigDecimal("15000.00")));
        String id=new tools.jackson.databind.ObjectMapper().readTree(response).path("changeRequestId").asText();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/company/change-requests/"+id+"/approve").with(jwt().jwt(t->t.subject(approver.getId().toString()))).contentType("application/json").content("{}"))
                .andExpect(status().isOk());
        var saved=services.findByIdAndCompanyId(f.service().getId(),f.company().getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(com.renovar.canteiro.io.contracts.domain.ContractServiceStatus.ACTIVE,saved.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(com.renovar.canteiro.io.contracts.domain.DiscountType.FIXED,saved.getDiscountType());
        org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("15000.00"),saved.getDiscountAmount());
        org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("285000.00"),saved.getNetAmount());
    }

    @Test void operationalUpdateAcceptsEqualDiscountEchoButCannotChangeOrImplicitlyReverseDiscount() throws Exception {
        var f=discountedService();
        mockMvc.perform(put(servicePath(f)).with(jwt().jwt(t->t.subject(f.user().getId().toString()))).contentType("application/json").content(operationalServiceBody(",\"discountType\":\"PERCENTAGE\",\"discountValue\":\"10.0\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.service.netAmount").value("270000.00"));
        var before=services.findByIdAndCompanyId(f.service().getId(),f.company().getId()).orElseThrow();
        for (String extra:java.util.List.of(",\"discountType\":\"PERCENTAGE\",\"discountValue\":\"5.0000\"",",\"discountType\":\"FIXED\",\"discountValue\":\"30000.00\"")) {
            mockMvc.perform(put(servicePath(f)).with(jwt().jwt(t->t.subject(f.user().getId().toString()))).contentType("application/json").content(operationalServiceBody(extra)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                    .andExpect(jsonPath("$.reason").value("SERVICE_DISCOUNT_CHANGE_REQUIRES_DEDICATED_OPERATION")).andExpect(jsonPath("$.correlationId").isNotEmpty());
        }
        for (String extra:java.util.List.of(",\"discountValue\":\"10.0\"",",\"discountType\":\"PERCENTAGE\"")) {
            mockMvc.perform(put(servicePath(f)).with(jwt().jwt(t->t.subject(f.user().getId().toString()))).contentType("application/json").content(operationalServiceBody(extra)))
                    .andExpect(status().isBadRequest());
        }
        var after=services.findByIdAndCompanyId(f.service().getId(),f.company().getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(before.getDiscountType(),after.getDiscountType());
        org.junit.jupiter.api.Assertions.assertEquals(before.getDiscountValue(),after.getDiscountValue());
        org.junit.jupiter.api.Assertions.assertEquals(before.getUpdatedAt(),after.getUpdatedAt());
    }

    @Test void updatingServiceWithoutPermissionOrFromAnotherTenantDoesNotChangeItsDiscount() throws Exception {
        var f=discountedService();var denied=createUser(f.company(),UUID.randomUUID()+"@example.test");var outsider=discountedService();
        mockMvc.perform(put(servicePath(f)).with(jwt().jwt(t->t.subject(denied.getId().toString()))).contentType("application/json").content(operationalServiceBody("")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(servicePath(f)).with(jwt().jwt(t->t.subject(outsider.user().getId().toString()))).contentType("application/json").content(operationalServiceBody("")))
                .andExpect(status().isNotFound());
        var after=services.findByIdAndCompanyId(f.service().getId(),f.company().getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(com.renovar.canteiro.io.contracts.domain.ContractServiceStatus.ACTIVE,after.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(new java.math.BigDecimal("270000.00"),after.getNetAmount());
    }

    private DiscountedServiceFixture discountedService() {
        var company=createCompany(UUID.randomUUID().toString());var user=createUser(company,UUID.randomUUID()+"@example.test");
        grant(user,company,AccessModule.SERVICES,AccessAction.UPDATE_DIRECT);grant(user,company,AccessModule.BILLING,AccessAction.READ);
        var work=workRepository.save(Work.create(company.getId(),customer(company,"Cliente").getId(),"Obra",null,WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION,null,WorkStatus.ACTIVE,null,null,null));
        var contract=contractRepository.save(Contract.create(company.getId(),work.getId(),"REF","Contrato sintético",ContractStatus.ACTIVE,null,null,null));
        var service=services.save(com.renovar.canteiro.io.contracts.domain.ContractService.create(company.getId(),contract.getId(),"Serviço",null,com.renovar.canteiro.io.contracts.domain.ContractServiceStatus.ACTIVE,java.math.BigDecimal.ONE,new java.math.BigDecimal("300000.00"),com.renovar.canteiro.io.contracts.domain.DiscountType.PERCENTAGE,new java.math.BigDecimal("10.0000")));
        return new DiscountedServiceFixture(company,user,contract,service);
    }
    private String servicePath(DiscountedServiceFixture f) { return "/api/v1/company/contracts/"+f.contract().getId()+"/services/"+f.service().getId(); }
    private String operationalServiceBody(String extra) { return "{\"name\":\"Serviço\",\"status\":\"ACTIVE\",\"quantity\":\"1.0000\",\"unitPrice\":\"300000.00\""+extra+"}"; }
    private record DiscountedServiceFixture(Company company,User user,Contract contract,com.renovar.canteiro.io.contracts.domain.ContractService service) { }

    @Test void contractDiscountIndicatorDistinguishesNoDiscountZeroEffectReversalAndPendingRequests() throws Exception {
        Company company=createCompany("discount-state");User user=createUser(company,UUID.randomUUID()+"@example.test");
        grant(user,company,AccessModule.CONTRACTS,AccessAction.READ);
        grant(user,company,AccessModule.DISCOUNTS,AccessAction.REQUEST_UPDATE,AccessAction.CANCEL_DIRECT);
        var work=workRepository.save(Work.create(company.getId(),customer(company,"Cliente").getId(),"Obra",null,WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION,null,WorkStatus.ACTIVE,null,null,null));
        var contract=contractRepository.save(Contract.create(company.getId(),work.getId(),"REF","Sem base",ContractStatus.ACTIVE,null,null,null));
        String path="/api/v1/company/contracts/"+contract.getId();
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.hasActiveContractDiscount").value(false));
        mockMvc.perform(put(path+"/discount").with(jwt().jwt(t->t.subject(user.getId().toString()))).contentType("application/json").content("{\"discountType\":\"PERCENTAGE\",\"discountValue\":\"10.0000\",\"justification\":\"Pedido sintético\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.hasActiveContractDiscount").value(false));
        discounts.save(com.renovar.canteiro.io.contracts.domain.ContractDiscount.create(company.getId(),contract.getId(),com.renovar.canteiro.io.contracts.domain.DiscountType.PERCENTAGE,new java.math.BigDecimal("10.0000")));
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.hasActiveContractDiscount").value(true));
        var requester=createUser(company,UUID.randomUUID()+"@example.test");
        grant(requester,company,AccessModule.CONTRACTS,AccessAction.READ);grant(requester,company,AccessModule.DISCOUNTS,AccessAction.REQUEST_CANCEL);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/discount/reversal").with(jwt().jwt(t->t.subject(requester.getId().toString()))).contentType("application/json").content("{\"justification\":\"Solicitação sintética\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.hasActiveContractDiscount").value(true));
        services.save(com.renovar.canteiro.io.contracts.domain.ContractService.create(company.getId(),contract.getId(),"Base positiva",null,com.renovar.canteiro.io.contracts.domain.ContractServiceStatus.ACTIVE,java.math.BigDecimal.ONE,new java.math.BigDecimal("100.00")));
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.hasActiveContractDiscount").value(true));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path+"/discount/reversal").with(jwt().jwt(t->t.subject(user.getId().toString()))).contentType("application/json").content("{\"justification\":\"Reversão sintética\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.hasActiveContractDiscount").value(false));
        mockMvc.perform(get("/api/v1/company/contracts").with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].hasActiveContractDiscount").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test void measurementAdjustmentDoesNotImplyActiveCommercialDiscountAndReadRemainsTenantAuthorized() throws Exception {
        var company=createCompany("adjustment-state");var user=createUser(company,UUID.randomUUID()+"@example.test");
        grant(user,company,AccessModule.CONTRACTS,AccessAction.READ);grant(user,company,AccessModule.BILLING,AccessAction.READ);
        var work=workRepository.save(Work.create(company.getId(),customer(company,"Cliente").getId(),"Obra",null,WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION,null,WorkStatus.ACTIVE,null,null,null));
        var contract=contractRepository.save(Contract.create(company.getId(),work.getId(),"REF","Ajuste",ContractStatus.ACTIVE,null,null,null));
        services.save(com.renovar.canteiro.io.contracts.domain.ContractService.create(company.getId(),contract.getId(),"Serviço",null,com.renovar.canteiro.io.contracts.domain.ContractServiceStatus.ACTIVE,java.math.BigDecimal.ONE,new java.math.BigDecimal("100.00")));
        org.mockito.Mockito.doReturn(new java.math.BigDecimal("10.00")).when(adjustments).approvedAmount(company.getId(),contract.getId());
        String path="/api/v1/company/contracts/"+contract.getId();
        mockMvc.perform(get("/api/v1/company/billing-control/contracts/"+contract.getId()).with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.contractDiscountAmount").value("10.00"));
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(user.getId().toString())))).andExpect(status().isOk()).andExpect(jsonPath("$.hasActiveContractDiscount").value(false));
        var denied=createUser(company,UUID.randomUUID()+"@example.test");
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(denied.getId().toString())))).andExpect(status().isForbidden());
        var other=createCompany("other-discount");var outsider=createUser(other,UUID.randomUUID()+"@example.test");grant(outsider,other,AccessModule.CONTRACTS,AccessAction.READ);
        mockMvc.perform(get(path).with(jwt().jwt(t->t.subject(outsider.getId().toString())))).andExpect(status().isNotFound());
    }

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
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"name":"Tentativa externa","executionLocationType":"PROVIDER_UNIT","status":"ACTIVE"}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/company/contracts/{contractId}", externalContract.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"name":"Tentativa externa","status":"ACTIVE"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void prioritizesOpenAndActiveContractsBeforePaginationInBothLists() throws Exception {
        Company company = createCompany("contract-sort");
        Company otherCompany = createCompany("contract-sort-other");
        User user = createUser(company, "contract-sort@example.com");
        grant(user, company, AccessModule.CONTRACTS, AccessAction.READ);
        grant(user, company, AccessModule.BILLING, AccessAction.READ);
        Work work = sortWork(company);
        Work externalWork = sortWork(otherCompany);
        Contract draft = sortContract(company, work, "A rascunho", ContractStatus.DRAFT, "2026-01-03");
        Contract active = sortContract(company, work, "Z ativo", ContractStatus.ACTIVE, "2026-01-02");
        Contract openA = sortContract(company, work, "C aberto", ContractStatus.OPEN, "2026-01-01");
        Contract openB = sortContract(company, work, "B aberto", ContractStatus.OPEN, "2026-01-01");
        Contract suspended = sortContract(company, work, "D suspenso", ContractStatus.SUSPENDED, "2026-01-01");
        sortContract(otherCompany, externalWork, "A externo", ContractStatus.OPEN, "2026-01-04");

        var tied = List.of(openA, openB).stream().sorted(Comparator.comparing(c -> c.getId().toString())).toList();
        var expected = List.of(active, tied.get(0), tied.get(1), draft, suspended);
        var session = jwt().jwt(token -> token.subject(user.getId().toString()));
        for (String path : List.of("/api/v1/company/contracts", "/api/v1/company/billing-control/contracts")) {
            String id = path.contains("billing-control") ? "contractId" : "id";
            for (int page = 0; page < expected.size(); page++) {
                mockMvc.perform(get(path).with(session)
                                .param("sort", "openFirst,asc").param("page", Integer.toString(page)).param("size", "1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalItems").value(5))
                        .andExpect(jsonPath("$.items[0]." + id).value(expected.get(page).getId().toString()));
            }
            mockMvc.perform(get(path).with(session).param("sort", "name,asc", "openFirst,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[*].name", contains("B aberto", "C aberto", "Z ativo", "A rascunho", "D suspenso")));
            mockMvc.perform(get(path).with(session).param("sort", "openFirst,desc").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0]." + id).value(draft.getId().toString()));
            mockMvc.perform(get(path).with(session).param("sort", "name,asc").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0]." + id).value(draft.getId().toString()));
            mockMvc.perform(get(path).with(session).param("sort", "openFirst,asc").param("status", "SUSPENDED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0]." + id).value(suspended.getId().toString()));
        }
    }

    @Test
    void enforcesReferenceWithinWorkAndPreservesLegacyDuplicates() throws Exception {
        Company company = createCompany("unique-contract-reference");
        Company otherCompany = createCompany("unique-contract-reference-other");
        User manager = createUser(company, "reference-manager@example.com");
        User outsider = createUser(otherCompany, "reference-outsider@example.com");
        grant(manager, company, AccessModule.CONTRACTS, AccessAction.CREATE_DIRECT, AccessAction.UPDATE_DIRECT);
        grant(outsider, otherCompany, AccessModule.CONTRACTS, AccessAction.CREATE_DIRECT);
        Work work = sortWork(company);
        Work otherWork = sortWork(company);
        Work externalWork = sortWork(otherCompany);

        mockMvc.perform(createContract(manager, work, "  AbC/01-A.  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contract.reference").value("AbC/01-A."));
        expectReferenceConflict(mockMvc.perform(createContract(manager, work, "abc/01-a.")));
        mockMvc.perform(createContract(manager, work, "abc/01-a"))
                .andExpect(status().isCreated());
        mockMvc.perform(createContract(manager, otherWork, "abc/01-a."))
                .andExpect(status().isCreated());
        mockMvc.perform(createContract(outsider, externalWork, "abc/01-a."))
                .andExpect(status().isCreated());
        mockMvc.perform(createContract(manager, externalWork, "abc/01-a."))
                .andExpect(status().isNotFound());
        mockMvc.perform(createContract(manager, work, "  "))
                .andExpect(status().isCreated());

        Contract legacy = contractRepository.save(Contract.create(company.getId(), work.getId(), null,
                "Legado", ContractStatus.DRAFT, null, null, null));
        jdbc.update("update contract set reference = ? where id = ?", "  aBc/01-a. ", legacy.getId());
        mockMvc.perform(updateContract(manager, legacy, "aBc/01-a."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contract.reference").value("aBc/01-a."));
        org.junit.jupiter.api.Assertions.assertEquals("  aBc/01-a. ",
                jdbc.queryForObject("select reference from contract where id = ?", String.class, legacy.getId()));
        expectReferenceConflict(mockMvc.perform(updateContract(manager, legacy, "abc/01-a")));
    }

    @Test
    void rechecksTheReferenceWhenApprovingCreationAndUpdate() throws Exception {
        Company company = createCompany("contract-reference-approval");
        User requester = createUser(company, "reference-requester@example.com");
        User approver = createUser(company, "reference-approver@example.com");
        grant(requester, company, AccessModule.CONTRACTS, AccessAction.REQUEST_CREATE, AccessAction.REQUEST_UPDATE);
        grant(approver, company, AccessModule.CONTRACTS, AccessAction.CREATE_DIRECT, AccessAction.APPROVE);
        Work work = sortWork(company);
        Contract source = contractRepository.save(Contract.create(company.getId(), work.getId(), "ORIGINAL",
                "Contrato", ContractStatus.DRAFT, null, null, null));

        String createRequest = acceptedChangeRequest(mockMvc.perform(createContract(requester, work, "RESERVA/A")));
        String updateRequest = acceptedChangeRequest(mockMvc.perform(updateContract(requester, source, "RESERVA/B")));
        mockMvc.perform(createContract(approver, work, "reserva/a")).andExpect(status().isCreated());
        mockMvc.perform(createContract(approver, work, "reserva/b")).andExpect(status().isCreated());

        for (String requestId : List.of(createRequest, updateRequest)) {
            expectReferenceConflict(mockMvc.perform(post("/api/v1/company/change-requests/{id}/approve", requestId)
                    .with(jwt().jwt(token -> token.subject(approver.getId().toString())))
                    .contentType("application/json").content("{}")));
            org.junit.jupiter.api.Assertions.assertEquals("PENDING", jdbc.queryForObject(
                    "select status from change_request where id = ?", String.class, UUID.fromString(requestId)));
        }
        org.junit.jupiter.api.Assertions.assertEquals("ORIGINAL",
                contractRepository.findByIdAndCompanyId(source.getId(), company.getId()).orElseThrow().getReference());
    }

    @Test
    void serializesConcurrentContractCreationsWithTheSameReference() throws Exception {
        Company company = createCompany("contract-reference-concurrency");
        User manager = createUser(company, "reference-concurrent@example.com");
        grant(manager, company, AccessModule.CONTRACTS, AccessAction.CREATE_DIRECT);
        Work work = sortWork(company);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(createContract(manager, work, "  Conc/26  ")).andReturn().getResponse().getStatus();
            });
            var second = pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(createContract(manager, work, "conc/26")).andReturn().getResponse().getStatus();
            });
            org.junit.jupiter.api.Assertions.assertEquals(List.of(201, 409),
                    List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)).stream().sorted().toList());
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createContract(User user, Work work, String reference) {
        return post("/api/v1/company/contracts").with(jwt().jwt(token -> token.subject(user.getId().toString())))
                .contentType("application/json")
                .content("""
                        {"workId":"%s","reference":"%s","name":"Contrato","status":"DRAFT","justification":"Conferido"}
                        """.formatted(work.getId(), reference));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder updateContract(User user, Contract contract, String reference) {
        return put("/api/v1/company/contracts/{contractId}", contract.getId())
                .with(jwt().jwt(token -> token.subject(user.getId().toString()))).contentType("application/json")
                .content("""
                        {"reference":"%s","name":"Contrato revisado","status":"DRAFT","justification":"Conferido"}
                        """.formatted(reference));
    }

    private void expectReferenceConflict(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.reason").value("CONTRACT_REFERENCE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.violations[0].field").value("reference"));
    }

    private String acceptedChangeRequest(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        var response = result.andExpect(status().isAccepted()).andReturn().getResponse();
        return new tools.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).path("changeRequestId").asText();
    }

    private Work sortWork(Company company) {
        return workRepository.save(Work.create(company.getId(), customer(company, "Cliente").getId(),
                "Obra", "SORT", WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null,
                WorkStatus.ACTIVE, null, null, null));
    }

    private Contract sortContract(Company company, Work work, String name, ContractStatus status, String createdOn) {
        Contract contract = contractRepository.save(Contract.create(company.getId(), work.getId(), null, name,
                status, null, null, null));
        jdbc.update("update contract set created_at = ? where id = ? and company_id = ?",
                java.sql.Timestamp.from(Instant.parse(createdOn + "T12:00:00Z")), contract.getId(), company.getId());
        return contract;
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
