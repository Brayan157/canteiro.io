package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.PlatformUser;
import org.springframework.stereotype.Component;

@Component
public class PlatformUserPersistenceMapper {

    public PlatformUserJpaEntity toJpaEntity(PlatformUser platformUser) {
        return new PlatformUserJpaEntity(platformUser.getUserId(), platformUser.getGlobalRole());
    }

    public PlatformUser toDomain(PlatformUserJpaEntity entity) {
        return PlatformUser.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getGlobalRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
