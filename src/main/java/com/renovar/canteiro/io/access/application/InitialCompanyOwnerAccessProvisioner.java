package com.renovar.canteiro.io.access.application;

import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InitialCompanyOwnerAccessProvisioner {

    public static final String ROLE_NAME = "Company Administrator";
    private static final String ROLE_DESCRIPTION = "Initial company owner with all currently active permissions";

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public InitialCompanyOwnerAccess provision(UUID companyId, UUID ownerUserId) {
        List<Permission> activePermissions = permissionRepository.findAll(Pageable.unpaged()).stream()
                .filter(Permission::isActive)
                .toList();
        if (activePermissions.isEmpty()) {
            throw new IllegalStateException("The active permission catalog is required to onboard a company");
        }

        Role role = roleRepository.save(Role.create(companyId, ROLE_NAME, ROLE_DESCRIPTION));
        activePermissions.forEach(permission -> rolePermissionRepository.save(
                RolePermission.create(role.getId(), permission.getId())
        ));
        userRoleRepository.save(UserRole.create(ownerUserId, role.getId(), companyId));

        List<String> permissionCodes = activePermissions.stream()
                .map(Permission::code)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new InitialCompanyOwnerAccess(role.getId(), role.getName(), permissionCodes);
    }
}
