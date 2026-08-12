package com.renovar.canteiro.io.governance;

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
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeRequestIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ChangeRequestService changeRequestService;

    @Autowired
    private ChangeRequestRepository changeRequestRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyUserRepository companyUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private TenantContextHolder tenantContextHolder;

    @AfterEach
    void clearTenantContext() {
        tenantContextHolder.clear();
    }

    @Test
    void createsTenantBoundChangeRequestWithSnapshotVersionRequesterAndPendingState() {
        Company company = createCompany("Change request");
        Company otherCompany = createCompany("Other company");
        User requester = createCompanyUser(company, "change-requester@example.com");
        grantPermissions(requester, company, AccessAction.REQUEST_UPDATE);
        tenantContextHolder.setCurrentTenant(new TenantContext(requester.getId(), company.getId()));
        UUID contractId = UUID.randomUUID();

        ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                AuditModule.CONTRACTS,
                ChangeRequestOperation.UPDATE,
                "Contract",
                contractId,
                7,
                new ChangeRequestSnapshot(
                        new AuditPayload(Map.of("status", "DRAFT", "amount", "100.00")),
                        new AuditPayload(Map.of("status", "ACTIVE", "amount", "120.00"))
                ),
                "Customer approved the adjustment"
        ));

        ChangeRequest persistedRequest = changeRequestRepository.findByIdAndCompanyId(changeRequest.getId(), company.getId())
                .orElseThrow();

        assertNotNull(changeRequest.getId());
        assertEquals(company.getId(), persistedRequest.getCompanyId());
        assertEquals(requester.getId(), persistedRequest.getRequesterUserId());
        assertEquals(ChangeRequestStatus.PENDING, persistedRequest.getStatus());
        assertEquals(1, persistedRequest.getRevision());
        assertEquals(7, persistedRequest.getEntityVersion());
        assertEquals("DRAFT", persistedRequest.getSnapshot().beforeData().values().get("status"));
        assertEquals("120.00", persistedRequest.getSnapshot().proposedData().values().get("amount"));
        assertFalse(changeRequestRepository.findByIdAndCompanyId(changeRequest.getId(), otherCompany.getId()).isPresent());
    }

    @Test
    void doesNotCreatePendingRequestWhenRequesterHasEffectiveDirectAuthority() {
        Company company = createCompany("Effective direct authority");
        User editorApprover = createCompanyUser(company, "editor-approver@example.com");
        grantPermissions(editorApprover, company, AccessAction.REQUEST_UPDATE, AccessAction.APPROVE);
        tenantContextHolder.setCurrentTenant(new TenantContext(editorApprover.getId(), company.getId()));

        ApiException exception = assertThrows(ApiException.class, () -> changeRequestService.create(
                new CreateChangeRequestCommand(
                        AuditModule.CONTRACTS,
                        ChangeRequestOperation.UPDATE,
                        "Contract",
                        UUID.randomUUID(),
                        0,
                        new ChangeRequestSnapshot(
                                new AuditPayload(Map.of("status", "DRAFT")),
                                new AuditPayload(Map.of("status", "ACTIVE"))
                        ),
                        "Directly authorized adjustment"
                )
        ));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(0, changeRequestRepository.findByCompanyId(company.getId(), PageRequest.of(0, 10)).getTotalElements());
    }

    private void grantPermissions(User user, Company company, AccessAction... actions) {
        Role role = roleRepository.save(Role.create(company.getId(), "Contract requester " + UUID.randomUUID(), null));
        for (AccessAction action : actions) {
            Permission permission = permissionRepository.findByModuleAndAction(AccessModule.CONTRACTS, action).orElseThrow();
            rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId()));
        }
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
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
        User user = userRepository.save(User.create(email, UserType.COMPANY));
        companyUserRepository.save(CompanyUser.create(user.getId(), company.getId()));
        return user;
    }
}
