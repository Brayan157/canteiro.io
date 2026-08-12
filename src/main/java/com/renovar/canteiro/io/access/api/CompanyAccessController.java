package com.renovar.canteiro.io.access.api;

import com.renovar.canteiro.io.access.api.request.CreateCompanyEmployeeRequest;
import com.renovar.canteiro.io.access.api.request.CreateRoleRequest;
import com.renovar.canteiro.io.access.api.request.ReplaceRolePermissionsRequest;
import com.renovar.canteiro.io.access.api.request.ReplaceUserRolesRequest;
import com.renovar.canteiro.io.access.api.request.UpdateRoleRequest;
import com.renovar.canteiro.io.access.api.response.EmployeeResponse;
import com.renovar.canteiro.io.access.api.response.ChangeAuthorizationResponse;
import com.renovar.canteiro.io.access.api.response.PermissionResponse;
import com.renovar.canteiro.io.access.api.response.RoleResponse;
import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.CompanyAccessManagementService;
import com.renovar.canteiro.io.access.application.CreateCompanyEmployeeCommand;
import com.renovar.canteiro.io.access.application.CreateRoleCommand;
import com.renovar.canteiro.io.access.application.ReplaceRolePermissionsCommand;
import com.renovar.canteiro.io.access.application.ReplaceUserRolesCommand;
import com.renovar.canteiro.io.access.application.UpdateRoleCommand;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/access")
@Tag(name = "Company access management")
@RequiredArgsConstructor
public class CompanyAccessController {

    private static final Set<String> EMPLOYEE_SORT_FIELDS = Set.of("createdAt", "updatedAt");
    private static final Set<String> ROLE_SORT_FIELDS = Set.of("name", "createdAt", "updatedAt");
    private static final Set<String> PERMISSION_SORT_FIELDS = Set.of("module", "action", "createdAt");

    private final CompanyAccessManagementService companyAccessManagementService;
    private final AccessAuthorizationService accessAuthorizationService;
    private final CompanyAccessApiMapper companyAccessApiMapper;

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registers a company employee and sends an activation email")
    public EmployeeResponse createEmployee(@Valid @RequestBody CreateCompanyEmployeeRequest request) {
        return companyAccessApiMapper.toResponse(
                companyAccessManagementService.createEmployee(new CreateCompanyEmployeeCommand(request.email()))
        );
    }

    @GetMapping("/employees")
    @Operation(summary = "Lists employees from the authenticated company")
    public PageResponse<EmployeeResponse> findEmployees(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(
                companyAccessManagementService.findEmployees(new PageQuery(page, size, sort).toPageable(EMPLOYEE_SORT_FIELDS)),
                companyAccessApiMapper::toResponse
        );
    }

    @PatchMapping("/employees/{userId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivates an employee without removing its history")
    public void deactivateEmployee(@PathVariable UUID userId) {
        companyAccessManagementService.deactivateEmployee(userId);
    }

    @PutMapping("/employees/{userId}/roles")
    @Operation(summary = "Replaces the active roles assigned to an employee")
    public EmployeeResponse replaceEmployeeRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody ReplaceUserRolesRequest request
    ) {
        companyAccessManagementService.replaceEmployeeRoles(userId, new ReplaceUserRolesCommand(request.roleIds()));
        return companyAccessApiMapper.toResponse(companyAccessManagementService.findEmployee(userId));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a role for the authenticated company")
    public RoleResponse createRole(@Valid @RequestBody CreateRoleRequest request) {
        return toRoleResponse(companyAccessManagementService.createRole(new CreateRoleCommand(request.name(), request.description())));
    }

    @GetMapping("/roles")
    @Operation(summary = "Lists roles from the authenticated company")
    public PageResponse<RoleResponse> findRoles(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(
                companyAccessManagementService.findRoles(new PageQuery(page, size, sort).toPageable(ROLE_SORT_FIELDS)),
                this::toRoleResponse
        );
    }

    @PutMapping("/roles/{roleId}")
    @Operation(summary = "Updates a company role")
    public RoleResponse updateRole(@PathVariable UUID roleId, @Valid @RequestBody UpdateRoleRequest request) {
        return toRoleResponse(companyAccessManagementService.updateRole(
                roleId,
                new UpdateRoleCommand(request.name(), request.description())
        ));
    }

    @PatchMapping("/roles/{roleId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivates a role without removing its history")
    public void deactivateRole(@PathVariable UUID roleId) {
        companyAccessManagementService.deactivateRole(roleId);
    }

    @PutMapping("/roles/{roleId}/permissions")
    @Operation(summary = "Replaces the controlled catalog permissions assigned to a role")
    public RoleResponse replaceRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody ReplaceRolePermissionsRequest request
    ) {
        Set<UUID> permissionIds = companyAccessManagementService.replaceRolePermissions(
                roleId,
                new ReplaceRolePermissionsCommand(request.permissionIds())
        );
        return companyAccessApiMapper.toResponse(
                companyAccessManagementService.findRole(roleId),
                permissionIds
        );
    }

    @GetMapping("/permissions")
    @Operation(summary = "Lists the controlled platform permission catalog")
    public PageResponse<PermissionResponse> findPermissions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(
                companyAccessManagementService.findPermissions(
                        new PageQuery(page, size, sort).toPageable(PERMISSION_SORT_FIELDS)
                ),
                companyAccessApiMapper::toResponse
        );
    }

    @GetMapping("/change-authorizations/{module}/{operation}")
    @Operation(summary = "Resolves the authenticated user's effective change authorization")
    public ChangeAuthorizationResponse resolveChangeAuthorization(
            @PathVariable AccessModule module,
            @PathVariable ChangeOperation operation
    ) {
        return new ChangeAuthorizationResponse(
                module,
                operation,
                accessAuthorizationService.requireChangeAuthorization(module, operation)
        );
    }

    private RoleResponse toRoleResponse(com.renovar.canteiro.io.access.domain.Role role) {
        return companyAccessApiMapper.toResponse(role, companyAccessManagementService.findRolePermissionIds(role.getId()));
    }
}
