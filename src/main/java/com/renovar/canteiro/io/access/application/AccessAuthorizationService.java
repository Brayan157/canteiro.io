package com.renovar.canteiro.io.access.application;

import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AccessAuthorizationService {

    private final TenantContextHolder tenantContextHolder;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public void requirePermission(AccessModule module, AccessAction action) {
        if (!activeActionsForCurrentTenant(module).contains(action)) {
            throw new AccessDeniedException("The authenticated user does not have the required permission");
        }
    }

    @Transactional(readOnly = true)
    public ChangeAuthorizationMode requireChangeAuthorization(AccessModule module, ChangeOperation operation) {
        Set<AccessAction> grantedActions = activeActionsForCurrentTenant(module);
        AccessAction directAction = directActionFor(operation);
        AccessAction requestAction = requestActionFor(operation);
        if (grantedActions.contains(directAction)
                || grantedActions.contains(requestAction) && grantedActions.contains(AccessAction.APPROVE)) {
            return ChangeAuthorizationMode.DIRECT;
        }
        if (grantedActions.contains(requestAction)) {
            return ChangeAuthorizationMode.REQUEST_APPROVAL;
        }
        throw new AccessDeniedException("The authenticated user does not have the required permission");
    }

    private Set<AccessAction> activeActionsForCurrentTenant(AccessModule module) {
        TenantContext tenant = tenantContextHolder.requireCurrentTenant();
        return userRoleRepository.findByUserIdAndCompanyId(tenant.userId(), tenant.companyId()).stream()
                .filter(UserRole::isActive)
                .map(UserRole::getRoleId)
                .map(roleId -> roleRepository.findByIdAndCompanyId(roleId, tenant.companyId()))
                .flatMap(java.util.Optional::stream)
                .filter(role -> role.isActive())
                .map(role -> rolePermissionRepository.findByRoleId(role.getId()))
                .flatMap(java.util.Collection::stream)
                .filter(RolePermission::isActive)
                .map(RolePermission::getPermissionId)
                .map(permissionRepository::findById)
                .flatMap(java.util.Optional::stream)
                .filter(Permission::isActive)
                .filter(permission -> permission.getModule() == module)
                .map(Permission::getAction)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private AccessAction directActionFor(ChangeOperation operation) {
        return switch (operation) {
            case CREATE -> AccessAction.CREATE_DIRECT;
            case UPDATE -> AccessAction.UPDATE_DIRECT;
            case CANCEL -> AccessAction.CANCEL_DIRECT;
        };
    }

    private AccessAction requestActionFor(ChangeOperation operation) {
        return switch (operation) {
            case CREATE -> AccessAction.REQUEST_CREATE;
            case UPDATE -> AccessAction.REQUEST_UPDATE;
            case CANCEL -> AccessAction.REQUEST_CANCEL;
        };
    }
}
