package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.User;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceMapper {

    public UserJpaEntity toJpaEntity(User user) {
        UserJpaEntity entity = new UserJpaEntity(user.getEmail(), user.getUserType(), user.getStatus());
        if (user.getPasswordHash() != null) {
            entity.updateCredentials(
                    user.getPasswordHash(), user.getPasswordChangedAt(), user.getActivatedAt(), user.getStatus()
            );
        }
        return entity;
    }

    public void updateJpaEntity(UserJpaEntity entity, User user) {
        if (user.getPasswordHash() != null) {
            entity.updateCredentials(
                    user.getPasswordHash(), user.getPasswordChangedAt(), user.getActivatedAt(), user.getStatus()
            );
            return;
        }
        entity.updateStatus(user.getStatus());
    }

    public User toDomain(UserJpaEntity entity) {
        return User.rehydrate(
                entity.getId(),
                entity.getEmail(),
                entity.getUserType(),
                entity.getStatus(),
                entity.getPasswordHash(),
                entity.getPasswordChangedAt(),
                entity.getActivatedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
