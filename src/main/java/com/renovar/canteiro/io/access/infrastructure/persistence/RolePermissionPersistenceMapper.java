package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.RolePermission;
import org.springframework.stereotype.Component;

@Component
public class RolePermissionPersistenceMapper {

    public RolePermissionJpaEntity toJpaEntity(RolePermission rolePermission) {
        return new RolePermissionJpaEntity(
                rolePermission.getRoleId(),
                rolePermission.getPermissionId(),
                rolePermission.isActive()
        );
    }

    public void updateJpaEntity(RolePermissionJpaEntity entity, RolePermission rolePermission) {
        entity.changeActive(rolePermission.isActive());
    }

    public RolePermission toDomain(RolePermissionJpaEntity entity) {
        return RolePermission.rehydrate(
                entity.getId(),
                entity.getRoleId(),
                entity.getPermissionId(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
