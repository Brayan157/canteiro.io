package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.PasswordResetToken;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetTokenPersistenceMapper {

    public PasswordResetTokenJpaEntity toJpaEntity(PasswordResetToken passwordResetToken) {
        PasswordResetTokenJpaEntity entity = new PasswordResetTokenJpaEntity(
                passwordResetToken.getUserId(),
                passwordResetToken.getTokenHash(),
                passwordResetToken.getExpiresAt()
        );
        if (passwordResetToken.getConsumedAt() != null) {
            entity.consume(passwordResetToken.getConsumedAt());
        }
        return entity;
    }

    public void updateJpaEntity(PasswordResetTokenJpaEntity entity, PasswordResetToken passwordResetToken) {
        if (passwordResetToken.getConsumedAt() != null) {
            entity.consume(passwordResetToken.getConsumedAt());
        }
    }

    public PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        return PasswordResetToken.rehydrate(
                entity.getId(), entity.getUserId(), entity.getTokenHash(), entity.getExpiresAt(), entity.getConsumedAt(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
