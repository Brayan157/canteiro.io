package com.renovar.canteiro.io.access.api;

import com.renovar.canteiro.io.access.api.response.EmployeeResponse;
import com.renovar.canteiro.io.access.api.response.PermissionResponse;
import com.renovar.canteiro.io.access.api.response.RoleResponse;
import com.renovar.canteiro.io.access.application.CompanyEmployee;
import com.renovar.canteiro.io.access.domain.Permission;
import com.renovar.canteiro.io.access.domain.Role;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CompanyAccessApiMapper {

    public EmployeeResponse toResponse(CompanyEmployee employee) {
        return new EmployeeResponse(
                employee.user().getId(),
                employee.user().getEmail(),
                employee.user().getStatus(),
                employee.roleIds(),
                employee.user().getCreatedAt(),
                employee.user().getUpdatedAt()
        );
    }

    public RoleResponse toResponse(Role role, Set<java.util.UUID> permissionIds) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.isActive(),
                permissionIds,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getModule(),
                permission.getAction(),
                permission.code(),
                permission.isActive()
        );
    }
}
