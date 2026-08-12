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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EffectiveChangeAuthorizationApiIntegrationTest extends AbstractPostgresIntegrationTest {

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
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void resolvesDirectAuthorityForDirectPermissionOrMatchingRequestAndApproval() throws Exception {
        Company company = createCompany();
        User editorApprover = createActiveCompanyUser(company);
        grantPermissions(
                editorApprover,
                company,
                AccessModule.CONTRACTS,
                AccessAction.REQUEST_UPDATE,
                AccessAction.APPROVE
        );
        User directEditor = createActiveCompanyUser(company);
        grantPermissions(directEditor, company, AccessModule.CONTRACTS, AccessAction.UPDATE_DIRECT);
        User cancelEditorApprover = createActiveCompanyUser(company);
        grantPermissions(
                cancelEditorApprover,
                company,
                AccessModule.CONTRACTS,
                AccessAction.REQUEST_CANCEL,
                AccessAction.APPROVE
        );

        assertAuthorization(editorApprover, "UPDATE", "DIRECT");
        assertAuthorization(directEditor, "UPDATE", "DIRECT");
        assertAuthorization(cancelEditorApprover, "CANCEL", "DIRECT");
    }

    @Test
    void keepsARequestPendingWhenApprovalIsMissingOrFromAnotherModule() throws Exception {
        Company company = createCompany();
        User requester = createActiveCompanyUser(company);
        grantPermissions(requester, company, AccessModule.CONTRACTS, AccessAction.REQUEST_UPDATE);
        User differentModuleApprover = createActiveCompanyUser(company);
        grantPermissions(
                differentModuleApprover,
                company,
                AccessModule.CONTRACTS,
                AccessAction.REQUEST_UPDATE
        );
        grantPermissions(differentModuleApprover, company, AccessModule.BILLING, AccessAction.APPROVE);
        User approverOnly = createActiveCompanyUser(company);
        grantPermissions(approverOnly, company, AccessModule.CONTRACTS, AccessAction.APPROVE);

        assertAuthorization(requester, "UPDATE", "REQUEST_APPROVAL");
        assertAuthorization(differentModuleApprover, "UPDATE", "REQUEST_APPROVAL");
        mockMvc.perform(get("/api/v1/company/access/change-authorizations/CONTRACTS/UPDATE")
                        .with(jwt().jwt(token -> token.subject(approverOnly.getId().toString()))))
                .andExpect(status().isForbidden());
    }

    private void assertAuthorization(User user, String operation, String mode) throws Exception {
        mockMvc.perform(get("/api/v1/company/access/change-authorizations/CONTRACTS/{operation}", operation)
                        .with(jwt().jwt(token -> token.subject(user.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.module").value("CONTRACTS"))
                .andExpect(jsonPath("$.operation").value(operation))
                .andExpect(jsonPath("$.mode").value(mode));
    }

    private void grantPermissions(User user, Company company, AccessModule module, AccessAction... actions) {
        Role role = roleRepository.save(Role.create(
                company.getId(),
                "Permission role " + UUID.randomUUID(),
                null
        ));
        for (AccessAction action : actions) {
            Permission permission = permissionRepository.findByModuleAndAction(module, action).orElseThrow();
            rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        }
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private Company createCompany() {
        String suffix = UUID.randomUUID().toString();
        return companyRepository.save(Company.create(
                "Authorization " + suffix,
                "Authorization",
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                suffix + "@example.com",
                null,
                null,
                null
        ));
    }

    private User createActiveCompanyUser(Company company) {
        User user = User.create(UUID.randomUUID() + "@example.com", UserType.COMPANY);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        User persistedUser = userRepository.save(user);
        companyUserRepository.save(CompanyUser.create(persistedUser.getId(), company.getId()));
        return persistedUser;
    }
}
