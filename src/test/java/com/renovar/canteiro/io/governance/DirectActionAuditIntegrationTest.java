package com.renovar.canteiro.io.governance;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.application.CompanyAccessManagementService;
import com.renovar.canteiro.io.access.application.CreateRoleCommand;
import com.renovar.canteiro.io.access.application.UpdateRoleCommand;
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
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.shared.infrastructure.web.CorrelationIdFilter;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DirectActionAuditIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "Canteiro#2026Seguro";
    private static final String CORRELATION_ID = "direct-action-audit-test";

    @Autowired
    private CompanyAccessManagementService companyAccessManagementService;

    @Autowired
    private AuditEventRepository auditEventRepository;

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
    private TenantContextHolder tenantContextHolder;

    @AfterEach
    void clearRequestContext() {
        tenantContextHolder.clear();
        MDC.remove(CorrelationIdFilter.HEADER_NAME);
    }

    @Test
    void recordsDirectRoleChangesWithTrustedActorAndBeforeAfterValues() {
        Company company = createCompany("Audit direct action");
        User administrator = createCompanyUser(company, "audit-administrator@example.com");
        grantRoleManagement(administrator, company);
        tenantContextHolder.setCurrentTenant(new TenantContext(administrator.getId(), company.getId()));
        MDC.put(CorrelationIdFilter.HEADER_NAME, CORRELATION_ID);

        Role role = companyAccessManagementService.createRole(new CreateRoleCommand("Financial manager", "Initial role"));
        companyAccessManagementService.updateRole(role.getId(), new UpdateRoleCommand("Financial manager", "Updated role"));

        List<AuditEvent> events = auditEventRepository.findByCompanyId(company.getId(), PageRequest.of(0, 20))
                .getContent();
        AuditEvent createEvent = events.stream()
                .filter(event -> event.getAction() == AuditAction.CREATE)
                .filter(event -> event.getEntityId().equals(role.getId()))
                .findFirst()
                .orElseThrow();
        AuditEvent updateEvent = events.stream()
                .filter(event -> event.getAction() == AuditAction.UPDATE)
                .filter(event -> event.getEntityId().equals(role.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(AuditModule.ROLES, createEvent.getModule());
        assertEquals(administrator.getId(), createEvent.getActorUserId());
        assertNull(createEvent.getBeforeData());
        assertEquals("Financial manager", createEvent.getAfterData().values().get("name"));
        assertEquals("Initial role", updateEvent.getBeforeData().values().get("description"));
        assertEquals("Updated role", updateEvent.getAfterData().values().get("description"));
        assertEquals(CORRELATION_ID, updateEvent.getMetadata().values().get("correlationId"));
    }

    private void grantRoleManagement(User user, Company company) {
        Role administratorRole = roleRepository.save(Role.create(company.getId(), "Audit role administrator", null));
        Permission manageRoles = permissionRepository.findByModuleAndAction(AccessModule.ROLES, AccessAction.MANAGE_ROLES)
                .orElseThrow();
        rolePermissionRepository.save(RolePermission.create(administratorRole.getId(), manageRoles.getId()));
        userRoleRepository.save(UserRole.create(user.getId(), administratorRole.getId(), company.getId()));
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
}
