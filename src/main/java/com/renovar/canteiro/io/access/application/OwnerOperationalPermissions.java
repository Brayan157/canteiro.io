package com.renovar.canteiro.io.access.application;

import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class OwnerOperationalPermissions {

    private static final Map<AccessModule, List<AccessAction>> ACTIONS = Map.of(
            AccessModule.WORKFORCE, List.of(AccessAction.MANAGE_USERS, AccessAction.MANAGE_ROLES),
            AccessModule.WORK_DIARY, List.of(AccessAction.READ, AccessAction.CREATE_DIRECT, AccessAction.UPDATE_DIRECT)
    );

    private final PermissionRepository permissionRepository;
    private final CompanyUserRepository companyUserRepository;

    public Stream<Permission> forUser(Role role, UUID userId) {
        return forUser(role, userId, ACTIONS.keySet().stream());
    }

    public Stream<Permission> forUser(Role role, UUID userId, Stream<AccessModule> modules) {
        if (!isProtectedOwnerRole(role)
                || !companyUserRepository.findByUserIdAndCompanyId(userId, role.getCompanyId())
                        .map(CompanyUser::isCompanyOwner).orElse(false)) {
            return Stream.empty();
        }
        return modules.flatMap(this::activePermissions);
    }

    public Stream<Permission> forRole(Role role) {
        return isProtectedOwnerRole(role)
                ? ACTIONS.keySet().stream().flatMap(this::activePermissions) : Stream.empty();
    }

    private boolean isProtectedOwnerRole(Role role) {
        return role.isActive() && role.isSystemProtected();
    }

    private Stream<Permission> activePermissions(AccessModule module) {
        return ACTIONS.getOrDefault(module, List.of()).stream()
                .map(action -> permissionRepository.findByModuleAndAction(module, action))
                .flatMap(Optional::stream)
                .filter(Permission::isActive);
    }
}
