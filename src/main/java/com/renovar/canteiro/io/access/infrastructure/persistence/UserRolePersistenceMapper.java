package com.renovar.canteiro.io.access.infrastructure.persistence;

import com.renovar.canteiro.io.access.domain.UserRole;
import org.springframework.stereotype.Component;

@Component
public class UserRolePersistenceMapper {

    public UserRoleJpaEntity toJpaEntity(UserRole userRole) {
        return new UserRoleJpaEntity(
                userRole.getUserId(),
                userRole.getRoleId(),
                userRole.getCompanyId(),
                userRole.isActive()
        );
    }

    public void updateJpaEntity(UserRoleJpaEntity entity, UserRole userRole) {
        entity.changeActive(userRole.isActive());
    }

    public UserRole toDomain(UserRoleJpaEntity entity) {
        return UserRole.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getRoleId(),
                entity.getCompanyId(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
