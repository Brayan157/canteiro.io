package com.renovar.canteiro.io.identity;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthenticatedUserApiIntegrationTest extends AbstractPostgresIntegrationTest {

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
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Test
    void returnsTrustedCompanyIdentityRolesPermissionsAndEffectiveAccess() throws Exception {
        Company company = companyRepository.save(Company.create(
                "Renovar Engenharia Ltda.", "Renovar Engenharia", "12345678000199",
                "financeiro@renovar.example", null, null, null
        ));
        User user = User.create("owner@renovar.example", UserType.COMPANY);
        user.activate(passwordHasher.hash(PASSWORD), Instant.now());
        user = userRepository.save(user);
        var userId = user.getId();
        companyUserRepository.save(CompanyUser.create(user.getId(), company.getId()));
        Role role = roleRepository.save(Role.create(company.getId(), "Financial administrator", "Financial access"));
        Permission permission = permissionRepository.findByModuleAndAction(AccessModule.BILLING, AccessAction.CREATE_DIRECT)
                .orElseThrow();
        rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(jwt().jwt(token -> token.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value("owner@renovar.example"))
                .andExpect(jsonPath("$.contextType").value("COMPANY"))
                .andExpect(jsonPath("$.company.id").value(company.getId().toString()))
                .andExpect(jsonPath("$.roles[0].id").value(role.getId().toString()))
                .andExpect(jsonPath("$.roles[0].name").value("Financial administrator"))
                .andExpect(jsonPath("$.permissions[0]").value("BILLING.CREATE_DIRECT"))
                .andExpect(jsonPath("$.access.level").value("FULL"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
