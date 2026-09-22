package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.Role;
import org.springframework.stereotype.Component;

@Component
public class RolePersistenceMapper {

    public RoleJpaEntity toJpaEntity(Role role) {
        return new RoleJpaEntity(
                role.getCompanyId(), role.getName(), role.getDescription(), role.isActive(), role.isSystemProtected()
        );
    }

    public void updateJpaEntity(RoleJpaEntity entity, Role role) {
        entity.update(role.getName(), role.getDescription(), role.isActive(), role.isSystemProtected());
    }

    public Role toDomain(RoleJpaEntity entity) {
        return Role.rehydrate(
                entity.getId(),
                entity.getCompanyId(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.isSystemProtected(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
