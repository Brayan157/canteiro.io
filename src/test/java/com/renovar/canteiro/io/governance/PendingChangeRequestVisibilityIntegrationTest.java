package com.renovar.canteiro.io.governance;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.application.CompanyAccessManagementService;
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
import com.renovar.canteiro.io.governance.application.ChangeRequestQueryService;
import com.renovar.canteiro.io.governance.application.ChangeRequestFilter;
import com.renovar.canteiro.io.governance.application.ChangeRequestListItem;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingChangeRequestVisibilityIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ChangeRequestService changeRequestService;

    @Autowired
    private ChangeRequestRepository changeRequestRepository;

    @Autowired
    private ChangeRequestQueryService changeRequestQueryService;

    @Autowired
    private CompanyAccessManagementService companyAccessManagementService;

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
    void keepsPendingProposalsOutOfOfficialOperationalQueries() {
        Company company = createCompany();
        User user = createCompanyUser(company);
        grantReadPermissions(user, company);
        Role officialRole = roleRepository.save(Role.create(
                company.getId(),
                "Financial reviewer",
                "Official description"
        ));

        tenantContextHolder.setCurrentTenant(new TenantContext(user.getId(), company.getId()));
        ChangeRequest pendingRequest = changeRequestService.create(new CreateChangeRequestCommand(
                AuditModule.ROLES,
                ChangeRequestOperation.UPDATE,
                "Role",
                officialRole.getId(),
                0,
                new ChangeRequestSnapshot(
                        new AuditPayload(Map.of("description", "Official description")),
                        new AuditPayload(Map.of("description", "Proposed description"))
                ),
                "Awaiting approval"
        ));
        ChangeRequest approvedRequest = changeRequestRepository.save(ChangeRequest.create(
                company.getId(),
                user.getId(),
                AuditModule.ROLES,
                ChangeRequestOperation.CREATE,
                "Role",
                null,
                0,
                new ChangeRequestSnapshot(null, new AuditPayload(Map.of("name", "Approved role"))),
                "Already decided"
        ));
        approvedRequest.approve(user.getId(), Instant.now(), "Approved before query");
        approvedRequest = changeRequestRepository.save(approvedRequest);
        UUID approvedRequestId = approvedRequest.getId();

        List<Role> officialRoles = companyAccessManagementService.findRoles(PageRequest.of(0, 20)).getContent();
        List<ChangeRequest> pendingRequests = changeRequestQueryService.findPending(PageRequest.of(0, 20)).getContent();

        Role persistedOfficialRole = officialRoles.stream()
                .filter(role -> role.getId().equals(officialRole.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals("Official description", persistedOfficialRole.getDescription());
        assertTrue(officialRoles.stream().noneMatch(role -> "Proposed description".equals(role.getDescription())));
        assertEquals(List.of(pendingRequest.getId()), pendingRequests.stream().map(ChangeRequest::getId).toList());
        assertTrue(pendingRequests.stream().noneMatch(request -> request.getId().equals(approvedRequestId)));
    }

    @Test
    void filtersTheApprovalInboxUsingOnlySafeHumanReadableEmployeeProjections() {
        Company company = createCompany();
        User requester = createCompanyUser(company);
        grantReadPermissions(requester, company);
        ChangeRequest employeeRequest = changeRequestRepository.save(ChangeRequest.create(
                company.getId(),
                requester.getId(), AuditModule.EMPLOYEES, ChangeRequestOperation.CREATE, "Employee", null, 0,
                new ChangeRequestSnapshot(null, new AuditPayload(Map.of(
                        "fullName", "Marina Souza", "jobTitle", "Encarregada", "department", "Obras",
                        "employmentType", "EMPLOYEE", "currentlyEmployed", true,
                        "documentNumber", "52998224725", "phone", "11999999999",
                        "compensationAmount", "4500.00", "pis", "12345678901"
                ))),
                "Awaiting review"
        ));
        tenantContextHolder.setCurrentTenant(new TenantContext(requester.getId(), company.getId()));

        List<ChangeRequestListItem> results = changeRequestQueryService.findPending(
                        new ChangeRequestFilter("Marina", AuditModule.EMPLOYEES, ChangeRequestOperation.CREATE,
                                requester.getId(), LocalDate.now(java.time.ZoneOffset.UTC), LocalDate.now(java.time.ZoneOffset.UTC)),
                        PageRequest.of(0, 10)
                ).getContent();

        assertEquals(1, results.size());
        ChangeRequestListItem item = results.getFirst();
        assertEquals(employeeRequest.getId(), item.changeRequest().getId());
        assertEquals("Marina Souza", item.entity().displayName());
        assertEquals("Marina Souza", item.proposedSummary().get("fullName"));
        assertEquals("Encarregada", item.proposedSummary().get("jobTitle"));
        assertEquals("Obras", item.proposedSummary().get("department"));
        assertEquals(true, item.proposedSummary().get("hasDocument"));
        assertEquals(true, item.proposedSummary().get("hasCompensationAmount"));
        assertTrue(item.proposedData().keySet().stream().noneMatch(key -> Set.of(
                "documentNumber", "pis", "phone", "compensationAmount", "birthDate"
        ).contains(key)));
    }

    private void grantReadPermissions(User user, Company company) {
        Role role = roleRepository.save(Role.create(company.getId(), "Governance reader " + UUID.randomUUID(), null));
        List<Permission> permissions = List.of(
                requirePermission(AccessModule.ROLES, AccessAction.READ),
                requirePermission(AccessModule.ROLES, AccessAction.REQUEST_UPDATE),
                requirePermission(AccessModule.AUDIT, AccessAction.READ)
        );
        permissions.forEach(permission -> rolePermissionRepository.save(RolePermission.create(role.getId(), permission.getId())));
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private Permission requirePermission(AccessModule module, AccessAction action) {
        return permissionRepository.findByModuleAndAction(module, action).orElseThrow();
    }

    private Company createCompany() {
        String suffix = UUID.randomUUID().toString();
        return companyRepository.save(Company.create(
                "Visibility " + suffix,
                "Visibility",
                Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),
                suffix + "@example.com",
                null,
                null,
                null
        ));
    }

    private User createCompanyUser(Company company) {
        User user = userRepository.save(User.create(UUID.randomUUID() + "@example.com", UserType.COMPANY));
        companyUserRepository.save(CompanyUser.create(user.getId(), company.getId()));
        return user;
    }
}
