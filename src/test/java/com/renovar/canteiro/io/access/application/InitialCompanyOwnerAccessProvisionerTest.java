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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialCompanyOwnerAccessProvisionerTest {

    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private UserRoleRepository userRoleRepository;

    private InitialCompanyOwnerAccessProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new InitialCompanyOwnerAccessProvisioner(
                permissionRepository, roleRepository, rolePermissionRepository, userRoleRepository
        );
    }

    @Test
    void grantsEveryActivePermissionToTheInitialCompanyOwner() {
        UUID companyId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Permission customersCreate = permission(AccessModule.CUSTOMERS, AccessAction.CREATE_DIRECT, true);
        Permission usersManage = permission(AccessModule.USERS, AccessAction.MANAGE_USERS, true);
        Permission inactiveRoleManagement = permission(AccessModule.ROLES, AccessAction.MANAGE_ROLES, false);
        Role persistedRole = Role.rehydrate(
                roleId,
                companyId,
                InitialCompanyOwnerAccessProvisioner.ROLE_NAME,
                "Initial company owner with all currently active permissions",
                true,
                NOW,
                NOW
        );
        when(permissionRepository.findAll(any())).thenReturn(new PageImpl<>(List.of(
                customersCreate, usersManage, inactiveRoleManagement
        )));
        when(roleRepository.save(any(Role.class))).thenReturn(persistedRole);

        InitialCompanyOwnerAccess result = provisioner.provision(companyId, ownerUserId);

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        ArgumentCaptor<RolePermission> rolePermissionCaptor = ArgumentCaptor.forClass(RolePermission.class);
        ArgumentCaptor<UserRole> userRoleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(roleRepository).save(roleCaptor.capture());
        verify(rolePermissionRepository, times(2)).save(rolePermissionCaptor.capture());
        verify(userRoleRepository).save(userRoleCaptor.capture());

        assertEquals(companyId, roleCaptor.getValue().getCompanyId());
        assertEquals(InitialCompanyOwnerAccessProvisioner.ROLE_NAME, roleCaptor.getValue().getName());
        assertEquals(Set.of(customersCreate.getId(), usersManage.getId()), rolePermissionCaptor.getAllValues().stream()
                .map(RolePermission::getPermissionId)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(ownerUserId, userRoleCaptor.getValue().getUserId());
        assertEquals(roleId, userRoleCaptor.getValue().getRoleId());
        assertEquals(companyId, userRoleCaptor.getValue().getCompanyId());
        assertEquals(roleId, result.roleId());
        assertEquals(List.of("CUSTOMERS.CREATE_DIRECT", "USERS.MANAGE_USERS"), result.permissionCodes());
    }

    @Test
    void rejectsOnboardingWhenTheActivePermissionCatalogIsEmpty() {
        when(permissionRepository.findAll(any())).thenReturn(Page.<Permission>empty());

        assertThrows(IllegalStateException.class, () -> provisioner.provision(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(roleRepository, rolePermissionRepository, userRoleRepository);
    }

    private Permission permission(AccessModule module, AccessAction action, boolean active) {
        return Permission.rehydrate(UUID.randomUUID(), module, action, active, NOW, NOW);
    }
}
