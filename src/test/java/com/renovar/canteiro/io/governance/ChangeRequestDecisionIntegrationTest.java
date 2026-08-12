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
import com.renovar.canteiro.io.governance.application.ApproveChangeRequestCommand;
import com.renovar.canteiro.io.governance.application.ChangeRequestDecisionService;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.application.RejectChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeRequestDecisionIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ChangeRequestService changeRequestService;

    @Autowired
    private ChangeRequestDecisionService changeRequestDecisionService;

    @Autowired
    private ChangeRequestRepository changeRequestRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyUserRepository companyUserRepository;

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
    void clearTenantContext() {
        tenantContextHolder.clear();
    }

    @Test
    void approvesAndRejectsRequestsAtomicallyWithAnAuthorizedDifferentUser() {
        Company company = createCompany("Decision");
        User requester = createCompanyUser(company, "decision-requester@example.com");
        User approver = createCompanyUser(company, "decision-approver@example.com");
        grantRequestPermission(requester, company);
        grantDecisionPermissions(approver, company);

        ChangeRequest approvalRequest = createPendingContractChangeRequest(requester, company, "DRAFT", "ACTIVE");
        tenantContextHolder.setCurrentTenant(new TenantContext(approver.getId(), company.getId()));
        ChangeRequest approvedRequest = changeRequestDecisionService.approve(
                new ApproveChangeRequestCommand(approvalRequest.getId(), "Verified by contract manager")
        );

        assertEquals(ChangeRequestStatus.APPROVED, approvedRequest.getStatus());
        assertEquals(approver.getId(), approvedRequest.getDecidedByUserId());
        assertEquals("Verified by contract manager", approvedRequest.getDecisionReason());
        assertTrue(auditEventRepository.findByCompanyId(company.getId(), PageRequest.of(0, 20)).getContent().stream()
                .anyMatch(event -> event.getAction() == AuditAction.APPROVE
                        && event.getEntityId().equals(approvalRequest.getId())));

        ChangeRequest rejectionRequest = createPendingContractChangeRequest(requester, company, "ACTIVE", "CANCELLED");
        tenantContextHolder.setCurrentTenant(new TenantContext(approver.getId(), company.getId()));
        ChangeRequest rejectedRequest = changeRequestDecisionService.reject(
                new RejectChangeRequestCommand(rejectionRequest.getId(), "The supporting document is missing")
        );

        assertEquals(ChangeRequestStatus.REJECTED, rejectedRequest.getStatus());
        assertEquals("The supporting document is missing", rejectedRequest.getDecisionReason());
        assertEquals(approver.getId(), rejectedRequest.getDecidedByUserId());
    }

    @Test
    void rejectsSelfApprovalAndKeepsPendingRequestUntouchedWhenRejectionReasonIsMissing() {
        Company company = createCompany("Decision safeguards");
        User requester = createCompanyUser(company, "safeguard-requester@example.com");
        grantRequestPermission(requester, company);
        ChangeRequest changeRequest = createPendingContractChangeRequest(requester, company, "DRAFT", "ACTIVE");
        grantDecisionPermissions(requester, company);
        tenantContextHolder.setCurrentTenant(new TenantContext(requester.getId(), company.getId()));

        assertThrows(AccessDeniedException.class, () -> changeRequestDecisionService.approve(
                new ApproveChangeRequestCommand(changeRequest.getId(), null)
        ));
        assertThrows(ApiException.class, () -> changeRequestDecisionService.reject(
                new RejectChangeRequestCommand(changeRequest.getId(), " ")
        ));

        ChangeRequest persistedRequest = changeRequestRepository.findByIdAndCompanyId(changeRequest.getId(), company.getId())
                .orElseThrow();
        assertEquals(ChangeRequestStatus.PENDING, persistedRequest.getStatus());
        assertNull(persistedRequest.getDecidedAt());
    }

    private ChangeRequest createPendingContractChangeRequest(User requester, Company company, String beforeStatus, String afterStatus) {
        tenantContextHolder.setCurrentTenant(new TenantContext(requester.getId(), company.getId()));
        return changeRequestService.create(new CreateChangeRequestCommand(
                AuditModule.CONTRACTS,
                ChangeRequestOperation.UPDATE,
                "Contract",
                UUID.randomUUID(),
                2,
                new ChangeRequestSnapshot(
                        new AuditPayload(Map.of("status", beforeStatus)),
                        new AuditPayload(Map.of("status", afterStatus))
                ),
                "Contract status adjustment"
        ));
    }

    private void grantDecisionPermissions(User user, Company company) {
        Role role = roleRepository.save(Role.create(company.getId(), "Contract approver " + UUID.randomUUID(), null));
        Permission approvePermission = permissionRepository.findByModuleAndAction(AccessModule.CONTRACTS, AccessAction.APPROVE)
                .orElseThrow();
        Permission rejectPermission = permissionRepository.findByModuleAndAction(AccessModule.CONTRACTS, AccessAction.REJECT)
                .orElseThrow();
        rolePermissionRepository.save(RolePermission.create(role.getId(), approvePermission.getId()));
        rolePermissionRepository.save(RolePermission.create(role.getId(), rejectPermission.getId()));
        userRoleRepository.save(UserRole.create(user.getId(), role.getId(), company.getId()));
    }

    private void grantRequestPermission(User user, Company company) {
        Role role = roleRepository.save(Role.create(company.getId(), "Contract requester " + UUID.randomUUID(), null));
        Permission requestPermission = permissionRepository.findByModuleAndAction(
                AccessModule.CONTRACTS,
                AccessAction.REQUEST_UPDATE
        ).orElseThrow();
        rolePermissionRepository.save(RolePermission.create(role.getId(), requestPermission.getId()));
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
