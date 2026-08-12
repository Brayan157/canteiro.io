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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";

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
    void persistsTenantRolePermissionAndUserAssignment() {
        Company company = createCompany("Access");
        User user = createCompanyUser(company, "access-user@example.com");
        Role role = roleRepository.save(Role.create(company.getId(), "Financial auditor", "Approves billing changes"));
        Permission permission = permissionRepository.findByModuleAndAction(
                AccessModule.BILLING,
                AccessAction.APPROVE
        ).orElseThrow();
        RolePermission rolePermission = rolePermissionRepository.save(
                RolePermission.create(role.getId(), permission.getId())
        );
        UserRole userRole = userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));

        assertEquals(role.getId(), roleRepository.findByIdAndCompanyId(role.getId(), company.getId()).orElseThrow().getId());
        assertEquals(permission.getId(), permissionRepository.findByModuleAndAction(
                AccessModule.BILLING,
                AccessAction.APPROVE
        ).orElseThrow().getId());
        assertEquals(permission.getId(), rolePermissionRepository.findByRoleId(role.getId()).getFirst().getPermissionId());
        assertEquals(userRole.getId(), userRoleRepository.findByUserIdAndCompanyId(
                user.getId(),
                company.getId()
        ).getFirst().getId());
        assertEquals(role.getId(), rolePermission.getRoleId());
    }

    @Test
    void rejectsRoleAssignmentWhenUserAndRoleBelongToDifferentCompanies() {
        Company firstCompany = createCompany("First access");
        Company secondCompany = createCompany("Second access");
        User firstCompanyUser = createCompanyUser(firstCompany, "first-access-user@example.com");
        Role secondCompanyRole = roleRepository.save(Role.create(
                secondCompany.getId(),
                "Second company role",
                null
        ));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userRoleRepository.save(UserRole.create(
                        firstCompanyUser.getId(),
                        secondCompanyRole.getId(),
                        firstCompany.getId()
                ))
        );
    }

    private Company createCompany(String suffix) {
        return companyRepository.save(Company.create(
                suffix + " Company Ltda.",
                suffix + " Company",
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
}
