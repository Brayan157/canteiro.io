package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.Permission;
import org.springframework.stereotype.Component;

@Component
public class PermissionPersistenceMapper {

    public PermissionJpaEntity toJpaEntity(Permission permission) {
        return new PermissionJpaEntity(permission.getModule(), permission.getAction(), permission.isActive());
    }

    public Permission toDomain(PermissionJpaEntity entity) {
        return Permission.rehydrate(
                entity.getId(),
                entity.getModule(),
                entity.getAction(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
