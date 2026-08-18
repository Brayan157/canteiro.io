package com.renovar.canteiro.io.employees;

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
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EmployeeApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyUserRepository companyUserRepository;
    @Autowired private PasswordHasher passwordHasher;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private EmployeeRepository employeeRepository;

    @Test
    void createsEmployeeWithoutEmailOrUserWhenAuthorized() throws Exception {
        Company company = createCompany("first");
        User manager = createUser(company, "manager-first@example.com");
        grant(manager, company, AccessAction.CREATE_DIRECT, AccessAction.READ);

        mockMvc.perform(post("/api/v1/company/employees")
                        .with(jwt().jwt(token -> token.subject(manager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"fullName\":\"Ana da Silva\",\"jobTitle\":\"Montadora\",\"phone\":\"11999999999\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employee.fullName").value("Ana da Silva"))
                .andExpect(jsonPath("$.employee.userId").isEmpty());
    }

    @Test
    void preventsAUserFromAnotherTenantFromReadingEmployee() throws Exception {
        Company firstCompany = createCompany("second-first");
        Company secondCompany = createCompany("second-other");
        User firstManager = createUser(firstCompany, "manager-second-first@example.com");
        User otherManager = createUser(secondCompany, "manager-second-other@example.com");
        grant(firstManager, firstCompany, AccessAction.CREATE_DIRECT, AccessAction.READ);
        grant(otherManager, secondCompany, AccessAction.READ);

        mockMvc.perform(post("/api/v1/company/employees")
                        .with(jwt().jwt(token -> token.subject(firstManager.getId().toString())))
                        .contentType("application/json")
                        .content("{\"fullName\":\"Ana da Silva\"}"))
                .andExpect(status().isCreated());
        UUID employeeId = employeeRepository.findByCompanyId(firstCompany.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().getFirst().getId();

        mockMvc.perform(get("/api/v1/company/employees/{employeeId}", employeeId)
                        .with(jwt().jwt(token -> token.subject(otherManager.getId().toString()))))
                .andExpect(status().isNotFound());
    }

    private void grant(User user, Company company, AccessAction... actions) {
        Role role = roleRepository.save(Role.create(company.getId(), "Employee role " + UUID.randomUUID(), null));
        for (AccessAction action : actions) {
            Permission permission = permissionRepository.findByModuleAndAction(AccessModule.EMPLOYEES, action).orElseThrow();
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
