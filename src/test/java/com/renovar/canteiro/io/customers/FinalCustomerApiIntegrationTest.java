package com.renovar.canteiro.io.customers;

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
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerAddressRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerContactRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FinalCustomerApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private FinalCustomerRepository finalCustomerRepository;
    @Autowired private FinalCustomerContactRepository contactRepository;
    @Autowired private FinalCustomerAddressRepository addressRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private UserRoleRepository userRoleRepository;

    @Test
    void filtersAndUpdatesFinalCustomersOnlyInsideTheAuthenticatedTenant() throws Exception {
        Company company = createCompany("filter-customer-api");
        Company otherCompany = createCompany("filter-customer-api-other");
        User manager = createUser(company, "customer-manager@example.com");
        User outsider = createUser(otherCompany, "customer-outsider@example.com");
        grant(manager, company, AccessAction.READ, AccessAction.UPDATE_DIRECT);
        grant(outsider, otherCompany, AccessAction.READ);

        FinalCustomer customer = finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.LEGAL, "Usina Horizonte", "12.345.678/0001-90"
        ));
        finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.INDIVIDUAL, "Ana Oliveira", "529.982.247-25"
        ));
        finalCustomerRepository.save(FinalCustomer.create(
                otherCompany.getId(), FinalCustomerType.LEGAL, "Usina Horizonte", "12.345.678/0001-90"
        ));

        mockMvc.perform(get("/api/v1/company/customers")
                        .param("name", "horiz")
                        .param("document", "12.345.678/0001")
                        .param("customerType", "LEGAL")
                        .param("active", "true")
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(customer.getId().toString()));

        mockMvc.perform(put("/api/v1/company/customers/{customerId}", customer.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"customerType":"LEGAL","name":"Usina Horizonte Renovada","document":"98.765.432/0001-10"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DIRECT"))
                .andExpect(jsonPath("$.customer.name").value("Usina Horizonte Renovada"))
                .andExpect(jsonPath("$.customer.document").value("98765432000110"));

        mockMvc.perform(put("/api/v1/company/customers/{customerId}", customer.getId())
                        .with(jwt().jwt(token -> token.subject(outsider.getId().toString())))
                        .contentType("application/json")
                        .content("""
                                {"customerType":"LEGAL","name":"Tentativa externa","document":"98.765.432/0001-10"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void managesCustomerContactsAndAddressesDirectlyWithoutCrossTenantAccessOrPhysicalDeletion() throws Exception {
        Company company = createCompany("customer-details-direct");
        Company otherCompany = createCompany("customer-details-other");
        User manager = createUser(company, "customer-details-manager@example.com");
        User unprivileged = createUser(company, "customer-details-unprivileged@example.com");
        User outsider = createUser(otherCompany, "customer-details-outsider@example.com");
        grant(manager, company, AccessAction.READ, AccessAction.CREATE_DIRECT, AccessAction.UPDATE_DIRECT, AccessAction.CANCEL_DIRECT);
        grant(outsider, otherCompany, AccessAction.READ);
        FinalCustomer customer = finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.LEGAL, "Cliente de Contatos", "12.345.678/0001-90"));

        mockMvc.perform(post("/api/v1/company/customers/{customerId}/contacts", customer.getId())
                        .with(jwt().jwt(token -> token.subject(unprivileged.getId().toString())))
                        .contentType("application/json")
                        .content("{\"name\":\"Sem Alçada\",\"email\":\"sem-alcada@example.com\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/company/customers/{customerId}/contacts", customer.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"name\":\"Ana Comercial\",\"email\":\"ana@example.com\",\"primaryContact\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("DIRECT"))
                .andExpect(jsonPath("$.contact.primaryContact").value(true));
        var contact = contactRepository.findByFinalCustomerIdAndCompanyId(customer.getId(), company.getId()).getFirst();

        mockMvc.perform(put("/api/v1/company/customers/{customerId}/contacts/{contactId}", customer.getId(), contact.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"name\":\"Ana Comercial Atualizada\",\"phone\":\"11999990000\",\"primaryContact\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.name").value("Ana Comercial Atualizada"));

        mockMvc.perform(put("/api/v1/company/customers/{customerId}/contacts/{contactId}/primary", customer.getId(), contact.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.primaryContact").value(true));

        mockMvc.perform(delete("/api/v1/company/customers/{customerId}/contacts/{contactId}", customer.getId(), contact.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isNoContent());
        assertFalse(contactRepository.findByIdAndFinalCustomerIdAndCompanyId(contact.getId(), customer.getId(), company.getId())
                .orElseThrow().isActive());

        mockMvc.perform(post("/api/v1/company/customers/{customerId}/addresses", customer.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"street\":\"Rua das Flores\",\"city\":\"São Paulo\",\"state\":\"SP\",\"country\":\"BR\",\"primaryAddress\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address.primaryAddress").value(true));
        var address = addressRepository.findByFinalCustomerIdAndCompanyId(customer.getId(), company.getId()).getFirst();

        mockMvc.perform(put("/api/v1/company/customers/{customerId}/addresses/{addressId}", customer.getId(), address.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"label\":\"Sede\",\"street\":\"Rua das Flores, 10\",\"city\":\"São Paulo\",\"state\":\"SP\",\"country\":\"BR\",\"primaryAddress\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.label").value("Sede"));

        mockMvc.perform(put("/api/v1/company/customers/{customerId}/addresses/{addressId}/primary", customer.getId(), address.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.primaryAddress").value(true));

        mockMvc.perform(delete("/api/v1/company/customers/{customerId}/addresses/{addressId}", customer.getId(), address.getId())
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString()))))
                .andExpect(status().isNoContent());
        assertFalse(addressRepository.findByIdAndFinalCustomerIdAndCompanyId(address.getId(), customer.getId(), company.getId())
                .orElseThrow().isActive());

        mockMvc.perform(get("/api/v1/company/customers/{customerId}/contacts", customer.getId())
                        .with(jwt().jwt(token -> token.subject(outsider.getId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitsContactAndAddressChangesForApprovalWithoutChangingOfficialData() throws Exception {
        Company company = createCompany("customer-details-approval");
        User requester = createUser(company, "customer-details-requester@example.com");
        grant(requester, company, AccessAction.REQUEST_CREATE);
        FinalCustomer customer = finalCustomerRepository.save(FinalCustomer.create(
                company.getId(), FinalCustomerType.LEGAL, "Cliente para Aprovação", "98.765.432/0001-10"));

        mockMvc.perform(post("/api/v1/company/customers/{customerId}/contacts", customer.getId())
                        .with(jwt().jwt(token -> token.subject(requester.getId().toString())))
                        .contentType("application/json")
                        .content("{\"name\":\"Contato Pendente\",\"email\":\"pendente@example.com\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mode").value("REQUEST_APPROVAL"))
                .andExpect(jsonPath("$.changeRequestId").isNotEmpty())
                .andExpect(jsonPath("$.contact").isEmpty());

        mockMvc.perform(post("/api/v1/company/customers/{customerId}/addresses", customer.getId())
                        .with(jwt().jwt(token -> token.subject(requester.getId().toString())))
                        .contentType("application/json")
                        .content("{\"street\":\"Avenida Pendente\",\"city\":\"Campinas\",\"state\":\"SP\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mode").value("REQUEST_APPROVAL"))
                .andExpect(jsonPath("$.changeRequestId").isNotEmpty())
                .andExpect(jsonPath("$.address").isEmpty());

        assertTrue(contactRepository.findByFinalCustomerIdAndCompanyId(customer.getId(), company.getId()).isEmpty());
        assertTrue(addressRepository.findByFinalCustomerIdAndCompanyId(customer.getId(), company.getId()).isEmpty());
    }

    private void grant(User user, Company company, AccessAction... actions) {
        Role role = roleRepository.save(Role.create(company.getId(), "Customer role " + UUID.randomUUID(), null));
        for (AccessAction action : actions) {
            Permission permission = permissionRepository.findByModuleAndAction(AccessModule.CUSTOMERS, action).orElseThrow();
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
        User persistedUser = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(persistedUser.getId(), company.getId()));
        return persistedUser;
    }
}
