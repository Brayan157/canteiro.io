package com.renovar.canteiro.io.access;

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
import com.renovar.canteiro.io.identity.application.AccountActivationEmailSender;
import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(CompanyAccessApiIntegrationTest.AccountActivationEmailTestConfiguration.class)
class CompanyAccessApiIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private AccountActivationTokenRepository accountActivationTokenRepository;

    @Autowired
    private ActivationTokenHasher activationTokenHasher;

    @Autowired
    private CapturingAccountActivationEmailSender accountActivationEmailSender;

    @Test
    void managesEmployeesRolesAndControlledAssignmentsWithinTheCurrentCompany() throws Exception {
        Company company = createCompany("Access API");
        User administrator = createCompanyUser(company, "administrator@example.com");
        grantAccessManagementPermissions(administrator, company);
        Permission billingApproval = permissionRepository.findByModuleAndAction(
                AccessModule.BILLING,
                AccessAction.APPROVE
        ).orElseThrow();

        mockMvc.perform(post("/api/v1/company/access/roles")
                        .with(jwt().jwt(token -> token.subject(administrator.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Financial auditor","description":"Approves billing changes"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Financial auditor"));
        Role role = roleRepository.findByCompanyId(company.getId(), PageRequest.of(0, 10)).getContent().stream()
                .filter(candidate -> candidate.getName().equals("Financial auditor"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(put("/api/v1/company/access/roles/{roleId}/permissions", role.getId())
                        .with(jwt().jwt(token -> token.subject(administrator.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"permissionIds\":[\"" + billingApproval.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionIds[0]").value(billingApproval.getId().toString()));

        accountActivationEmailSender.clear();
        mockMvc.perform(post("/api/v1/company/access/employees")
                        .with(jwt().jwt(token -> token.subject(administrator.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"email\":\"employee@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("employee@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"))
                .andExpect(jsonPath("$.activationToken").doesNotExist());
        User employee = userRepository.findByEmail("employee@example.com").orElseThrow();

        mockMvc.perform(put("/api/v1/company/access/employees/{userId}/roles", employee.getId())
                        .with(jwt().jwt(token -> token.subject(administrator.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"roleIds\":[\"" + role.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleIds[0]").value(role.getId().toString()));

        UserRole userRole = userRoleRepository.findByUserIdAndRoleIdAndCompanyId(
                employee.getId(),
                role.getId(),
                company.getId()
        ).orElseThrow();
        String rawActivationToken = accountActivationEmailSender.rawActivationToken();

        assertTrue(userRole.isActive());
        assertNotNull(rawActivationToken);
        assertEquals("employee@example.com", accountActivationEmailSender.email());
        assertTrue(accountActivationTokenRepository.findByTokenHash(activationTokenHasher.hash(rawActivationToken)).isPresent());
        mockMvc.perform(get("/api/v1/company/access/permissions")
                        .with(jwt().jwt(token -> token.subject(administrator.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(152));
    }

    @Test
    void doesNotReadOrMutateEmployeesAndRolesFromAnotherCompany() throws Exception {
        Company firstCompany = createCompany("First company");
        Company secondCompany = createCompany("Second company");
        User firstCompanyUser = createCompanyUser(firstCompany, "first-company-user@example.com");
        User secondCompanyEmployee = createCompanyUser(secondCompany, "second-company-employee@example.com");
        Role secondCompanyRole = roleRepository.save(Role.create(secondCompany.getId(), "Second company role", null));
        grantAccessManagementPermissions(firstCompanyUser, firstCompany);

        mockMvc.perform(get("/api/v1/company/access/employees")
                        .with(jwt().jwt(token -> token.subject(firstCompanyUser.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(firstCompanyUser.getId().toString()));
        mockMvc.perform(put("/api/v1/company/access/employees/{userId}/roles", secondCompanyEmployee.getId())
                        .with(jwt().jwt(token -> token.subject(firstCompanyUser.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/company/access/roles/{roleId}/permissions", secondCompanyRole.getId())
                        .with(jwt().jwt(token -> token.subject(firstCompanyUser.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[]}"))
                .andExpect(status().isNotFound());

        assertTrue(userRoleRepository.findByUserIdAndCompanyId(
                secondCompanyEmployee.getId(),
                secondCompany.getId()
        ).isEmpty());
        assertEquals("Second company role", roleRepository.findByIdAndCompanyId(
                secondCompanyRole.getId(),
                secondCompany.getId()
        ).orElseThrow().getName());
    }

    @Test
    void deniesUseCasesWhenTheAuthenticatedUserDoesNotHaveTheRequiredPermission() throws Exception {
        Company company = createCompany("Unauthorized access");
        User user = createCompanyUser(company, "unauthorized-user@example.com");

        mockMvc.perform(get("/api/v1/company/access/employees")
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create(
                suffix + " Ltda.",
                suffix,
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                UUID.randomUUID() + "@example.com",
                null,
                null,
                null
        ));
    }

    private User createCompanyUser(Company company, String email) {
        User user = User.create(email, UserType.COMPANY);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        User persistedUser = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(persistedUser.getId(), company.getId()));
        return persistedUser;
    }

    private void grantAccessManagementPermissions(User user, Company company) {
        Role administratorRole = roleRepository.save(Role.create(
                company.getId(),
                "Access administrator " + UUID.randomUUID(),
                "Manages company users and roles"
        ));
        List<Permission> permissions = List.of(
                requirePermission(AccessModule.USERS, AccessAction.READ),
                requirePermission(AccessModule.USERS, AccessAction.MANAGE_USERS),
                requirePermission(AccessModule.ROLES, AccessAction.READ),
                requirePermission(AccessModule.ROLES, AccessAction.MANAGE_ROLES)
        );
        permissions.forEach(permission -> rolePermissionRepository.save(
                RolePermission.create(administratorRole.getId(), permission.getId())
        ));
        userRoleRepository.save(UserRole.create(user.getId(), administratorRole.getId(), company.getId()));
    }

    private Permission requirePermission(AccessModule module, AccessAction action) {
        return permissionRepository.findByModuleAndAction(module, action).orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AccountActivationEmailTestConfiguration {

        @Bean
        @Primary
        CapturingAccountActivationEmailSender accountActivationEmailSender() {
            return new CapturingAccountActivationEmailSender();
        }
    }

    static class CapturingAccountActivationEmailSender implements AccountActivationEmailSender {

        private String email;
        private String rawActivationToken;

        @Override
        public void send(String email, String rawActivationToken, Instant expiresAt) {
            this.email = email;
            this.rawActivationToken = rawActivationToken;
        }

        String email() {
            return email;
        }

        String rawActivationToken() {
            return rawActivationToken;
        }

        void clear() {
            email = null;
            rawActivationToken = null;
        }
    }
}
