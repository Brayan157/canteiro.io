package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.RefreshToken;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenPersistenceMapper {

    public RefreshTokenJpaEntity toJpaEntity(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity(
                refreshToken.getUserId(),
                refreshToken.getTokenHash(),
                refreshToken.getExpiresAt()
        );
        if (refreshToken.getRevokedAt() != null) {
            entity.revoke(refreshToken.getRevokedAt(), refreshToken.getReplacedByTokenId());
        }
        return entity;
    }

    public void updateJpaEntity(RefreshTokenJpaEntity entity, RefreshToken refreshToken) {
        if (refreshToken.getRevokedAt() != null) {
            entity.revoke(refreshToken.getRevokedAt(), refreshToken.getReplacedByTokenId());
        }
    }

    public RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getReplacedByTokenId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
