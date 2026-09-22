package com.renovar.canteiro.io.identity.application;

import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import com.renovar.canteiro.io.identity.api.response.AuthenticatedUserResponse;
import com.renovar.canteiro.io.identity.api.response.AuthenticationContextType;
import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.platform.subscription.application.CompanySubscriptionAccessService;
import com.renovar.canteiro.io.tenancy.application.TenantAuthenticationException;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticatedUserQueryService {

    private final UserRepository userRepository;
    private final TenantContextHolder tenantContextHolder;
    private final CompanyRepository companyRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final CompanySubscriptionAccessService companySubscriptionAccessService;
    private final PlatformUserRepository platformUserRepository;

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse currentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new TenantAuthenticationException("Authenticated user was not found"));
        return switch (user.getUserType()) {
            case COMPANY -> companyResponse(user);
            case PLATFORM -> platformResponse(user);
        };
    }

    private AuthenticatedUserResponse companyResponse(User user) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new TenantAuthenticationException("Authenticated company was not found"));
        List<Role> roles = userRoleRepository.findByUserIdAndCompanyId(user.getId(), companyId).stream()
                .filter(UserRole::isActive)
                .map(UserRole::getRoleId)
                .map(roleId -> roleRepository.findByIdAndCompanyId(roleId, companyId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(Role::isActive)
                .sorted(Comparator.comparing(Role::getName))
                .toList();

        List<String> permissions = roles.stream()
                .flatMap(role -> rolePermissionRepository.findByRoleId(role.getId()).stream())
                .filter(RolePermission::isActive)
                .map(RolePermission::getPermissionId)
                .distinct()
                .map(permissionId -> permissionRepository.findById(permissionId).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(Permission::isActive)
                .map(Permission::code)
                .sorted()
                .toList();

        return new AuthenticatedUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUserType(),
                user.getStatus(),
                AuthenticationContextType.COMPANY,
                new AuthenticatedUserResponse.CompanySummary(
                        company.getId(),
                        company.getCorporateName(),
                        company.getTradeName(),
                        company.getLogo() == null ? null : "/api/v1/company/logo",
                        company.isActive()
                ),
                null,
                roles.stream()
                        .map(role -> new AuthenticatedUserResponse.RoleSummary(role.getId(), role.getName()))
                        .toList(),
                permissions,
                new AuthenticatedUserResponse.AccessStatus(
                        companySubscriptionAccessService.resolveAccessLevel(companyId)
                )
        );
    }

    private AuthenticatedUserResponse platformResponse(User user) {
        PlatformUser platformUser = platformUserRepository.findByUserId(user.getId())
                .orElseThrow(() -> new TenantAuthenticationException("Authenticated platform user was not found"));
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUserType(),
                user.getStatus(),
                AuthenticationContextType.PLATFORM,
                null,
                platformUser.getGlobalRole(),
                List.of(),
                List.of(),
                null
        );
    }
}
