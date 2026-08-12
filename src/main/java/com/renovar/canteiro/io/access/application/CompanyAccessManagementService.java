package com.renovar.canteiro.io.access.application;

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
import com.renovar.canteiro.io.identity.application.AccountActivationProperties;
import com.renovar.canteiro.io.identity.application.ActivationTokenGenerator;
import com.renovar.canteiro.io.identity.application.ActivationTokenHasher;
import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyAccessManagementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final AuditEventRecorder auditEventRecorder;
    private final UserRepository userRepository;
    private final CompanyUserRepository companyUserRepository;
    private final AccountActivationTokenRepository accountActivationTokenRepository;
    private final ActivationTokenGenerator activationTokenGenerator;
    private final ActivationTokenHasher activationTokenHasher;
    private final AccountActivationEmailSender accountActivationEmailSender;
    private final AccountActivationProperties accountActivationProperties;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final Clock clock;

    @Transactional
    public CompanyEmployee createEmployee(CreateCompanyEmployeeCommand command) {
        requireUsersManagement();
        UUID companyId = currentCompanyId();
        String email = command.email().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, "Email is already in use");
        }

        User employee = userRepository.save(User.create(email, UserType.COMPANY));
        companyUserRepository.save(CompanyUser.create(employee.getId(), companyId));
        createActivationAndSendEmail(employee);
        auditEventRecorder.recordDirectAction(
                AuditModule.USERS,
                AuditAction.CREATE,
                "User",
                employee.getId(),
                null,
                employeeAuditData(employee),
                Map.of()
        );
        return new CompanyEmployee(employee, Set.of());
    }

    @Transactional(readOnly = true)
    public Page<CompanyEmployee> findEmployees(Pageable pageable) {
        requireUsersRead();
        UUID companyId = currentCompanyId();
        return companyUserRepository.findByCompanyId(companyId, pageable).map(companyUser -> {
            User user = userRepository.findById(companyUser.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Company user must reference an existing user"));
            return new CompanyEmployee(user, activeRoleIds(user.getId(), companyId));
        });
    }

    @Transactional(readOnly = true)
    public CompanyEmployee findEmployee(UUID userId) {
        requireUsersRead();
        UUID companyId = currentCompanyId();
        User user = findCompanyUser(userId);
        return new CompanyEmployee(user, activeRoleIds(user.getId(), companyId));
    }

    @Transactional
    public void deactivateEmployee(UUID userId) {
        requireUsersManagement();
        User employee = findCompanyUser(userId);
        Map<String, Object> beforeData = employeeAuditData(employee);
        employee.deactivate();
        userRepository.save(employee);
        auditEventRecorder.recordDirectAction(
                AuditModule.USERS,
                AuditAction.DEACTIVATE,
                "User",
                employee.getId(),
                beforeData,
                employeeAuditData(employee),
                Map.of()
        );
    }

    @Transactional
    public Role createRole(CreateRoleCommand command) {
        requireRolesManagement();
        Role role = roleRepository.save(Role.create(currentCompanyId(), command.name(), command.description()));
        auditEventRecorder.recordDirectAction(
                AuditModule.ROLES,
                AuditAction.CREATE,
                "Role",
                role.getId(),
                null,
                roleAuditData(role),
                Map.of()
        );
        return role;
    }

    @Transactional(readOnly = true)
    public Page<Role> findRoles(Pageable pageable) {
        requireRolesRead();
        return roleRepository.findByCompanyId(currentCompanyId(), pageable);
    }

    @Transactional(readOnly = true)
    public Role findRole(UUID roleId) {
        requireRolesRead();
        return findCurrentCompanyRole(roleId);
    }

    @Transactional
    public Role updateRole(UUID roleId, UpdateRoleCommand command) {
        requireRolesManagement();
        Role role = findCurrentCompanyRole(roleId);
        Map<String, Object> beforeData = roleAuditData(role);
        role.update(command.name(), command.description());
        Role updatedRole = roleRepository.save(role);
        auditEventRecorder.recordDirectAction(
                AuditModule.ROLES,
                AuditAction.UPDATE,
                "Role",
                updatedRole.getId(),
                beforeData,
                roleAuditData(updatedRole),
                Map.of()
        );
        return updatedRole;
    }

    @Transactional
    public void deactivateRole(UUID roleId) {
        requireRolesManagement();
        Role role = findCurrentCompanyRole(roleId);
        Map<String, Object> beforeData = roleAuditData(role);
        role.deactivate();
        Role deactivatedRole = roleRepository.save(role);
        auditEventRecorder.recordDirectAction(
                AuditModule.ROLES,
                AuditAction.DEACTIVATE,
                "Role",
                deactivatedRole.getId(),
                beforeData,
                roleAuditData(deactivatedRole),
                Map.of()
        );
    }

    @Transactional
    public Set<UUID> replaceRolePermissions(UUID roleId, ReplaceRolePermissionsCommand command) {
        requireRolesManagement();
        Role role = findCurrentCompanyRole(roleId);
        Set<UUID> permissionIds = new HashSet<>(command.permissionIds());
        Set<UUID> previousPermissionIds = activePermissionIds(role.getId());
        permissionIds.forEach(this::requireActivePermission);
        Map<UUID, RolePermission> existingByPermissionId = new HashMap<>();
        rolePermissionRepository.findByRoleId(role.getId())
                .forEach(rolePermission -> existingByPermissionId.put(rolePermission.getPermissionId(), rolePermission));

        existingByPermissionId.values().stream()
                .filter(rolePermission -> !permissionIds.contains(rolePermission.getPermissionId()))
                .filter(RolePermission::isActive)
                .forEach(rolePermission -> {
                    rolePermission.deactivate();
                    rolePermissionRepository.save(rolePermission);
                });
        permissionIds.forEach(permissionId -> activateRolePermission(role.getId(), permissionId, existingByPermissionId));
        Set<UUID> currentPermissionIds = activePermissionIds(role.getId());
        auditEventRecorder.recordDirectAction(
                AuditModule.ROLES,
                AuditAction.UPDATE,
                "RolePermissions",
                role.getId(),
                relationshipAuditData("permissionIds", previousPermissionIds),
                relationshipAuditData("permissionIds", currentPermissionIds),
                Map.of()
        );
        return currentPermissionIds;
    }

    @Transactional(readOnly = true)
    public Page<Permission> findPermissions(Pageable pageable) {
        requireRolesRead();
        return permissionRepository.findAll(pageable);
    }

    @Transactional
    public Set<UUID> replaceEmployeeRoles(UUID userId, ReplaceUserRolesCommand command) {
        requireUsersManagement();
        UUID companyId = currentCompanyId();
        User employee = findCompanyUser(userId);
        Set<UUID> roleIds = new HashSet<>(command.roleIds());
        Set<UUID> previousRoleIds = activeRoleIds(employee.getId(), companyId);
        roleIds.forEach(this::requireActiveCurrentCompanyRole);
        Map<UUID, UserRole> existingByRoleId = new HashMap<>();
        userRoleRepository.findByUserIdAndCompanyId(employee.getId(), companyId)
                .forEach(userRole -> existingByRoleId.put(userRole.getRoleId(), userRole));

        existingByRoleId.values().stream()
                .filter(userRole -> !roleIds.contains(userRole.getRoleId()))
                .filter(UserRole::isActive)
                .forEach(userRole -> {
                    userRole.deactivate();
                    userRoleRepository.save(userRole);
                });
        roleIds.forEach(roleId -> activateUserRole(employee.getId(), roleId, companyId, existingByRoleId));
        Set<UUID> currentRoleIds = activeRoleIds(employee.getId(), companyId);
        auditEventRecorder.recordDirectAction(
                AuditModule.USERS,
                AuditAction.UPDATE,
                "UserRoles",
                employee.getId(),
                relationshipAuditData("roleIds", previousRoleIds),
                relationshipAuditData("roleIds", currentRoleIds),
                Map.of()
        );
        return currentRoleIds;
    }

    @Transactional(readOnly = true)
    public Set<UUID> findRolePermissionIds(UUID roleId) {
        requireRolesRead();
        return activePermissionIds(findCurrentCompanyRole(roleId).getId());
    }

    private void createActivationAndSendEmail(User employee) {
        String rawActivationToken = activationTokenGenerator.generate();
        Instant expiresAt = clock.instant().plus(accountActivationProperties.tokenTtl());
        accountActivationTokenRepository.save(AccountActivationToken.create(
                employee.getId(),
                activationTokenHasher.hash(rawActivationToken),
                expiresAt
        ));
        accountActivationEmailSender.send(employee.getEmail(), rawActivationToken, expiresAt);
    }

    private User findCompanyUser(UUID userId) {
        UUID companyId = currentCompanyId();
        companyUserRepository.findByUserIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Employee"));
        return userRepository.findById(userId).orElseThrow(() -> new TenantResourceNotFoundException("Employee"));
    }

    private Role findCurrentCompanyRole(UUID roleId) {
        return roleRepository.findByIdAndCompanyId(roleId, currentCompanyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Role"));
    }

    private void requireActiveCurrentCompanyRole(UUID roleId) {
        if (!findCurrentCompanyRole(roleId).isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, "Role is inactive");
        }
    }

    private void requireActivePermission(UUID permissionId) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Permission"));
        if (!permission.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, "Permission is inactive");
        }
    }

    private void activateRolePermission(
            UUID roleId,
            UUID permissionId,
            Map<UUID, RolePermission> existingByPermissionId
    ) {
        RolePermission existingRolePermission = existingByPermissionId.get(permissionId);
        if (existingRolePermission == null) {
            rolePermissionRepository.save(RolePermission.create(roleId, permissionId));
        } else if (!existingRolePermission.isActive()) {
            existingRolePermission.activate();
            rolePermissionRepository.save(existingRolePermission);
        }
    }

    private void activateUserRole(UUID userId, UUID roleId, UUID companyId, Map<UUID, UserRole> existingByRoleId) {
        UserRole existingUserRole = existingByRoleId.get(roleId);
        if (existingUserRole == null) {
            userRoleRepository.save(UserRole.create(userId, roleId, companyId));
        } else if (!existingUserRole.isActive()) {
            existingUserRole.activate();
            userRoleRepository.save(existingUserRole);
        }
    }

    private Set<UUID> activePermissionIds(UUID roleId) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .filter(RolePermission::isActive)
                .map(RolePermission::getPermissionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Set<UUID> activeRoleIds(UUID userId, UUID companyId) {
        return userRoleRepository.findByUserIdAndCompanyId(userId, companyId).stream()
                .filter(UserRole::isActive)
                .map(UserRole::getRoleId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private UUID currentCompanyId() {
        return tenantContextHolder.requireCurrentTenant().companyId();
    }

    private void requireUsersRead() {
        accessAuthorizationService.requirePermission(AccessModule.USERS, AccessAction.READ);
    }

    private void requireUsersManagement() {
        accessAuthorizationService.requirePermission(AccessModule.USERS, AccessAction.MANAGE_USERS);
    }

    private void requireRolesRead() {
        accessAuthorizationService.requirePermission(AccessModule.ROLES, AccessAction.READ);
    }

    private void requireRolesManagement() {
        accessAuthorizationService.requirePermission(AccessModule.ROLES, AccessAction.MANAGE_ROLES);
    }

    private Map<String, Object> employeeAuditData(User employee) {
        return Map.of(
                "email", employee.getEmail(),
                "status", employee.getStatus().name(),
                "userType", employee.getUserType().name()
        );
    }

    private Map<String, Object> roleAuditData(Role role) {
        Map<String, Object> roleData = new HashMap<>();
        roleData.put("name", role.getName());
        roleData.put("description", role.getDescription());
        roleData.put("active", role.isActive());
        return roleData;
    }

    private Map<String, Object> relationshipAuditData(String key, Set<UUID> relationshipIds) {
        return Map.of(key, relationshipIds.stream().map(UUID::toString).sorted().toList());
    }
}
