package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import org.springframework.stereotype.Component;

@Component
public class AccountActivationTokenPersistenceMapper {

    public AccountActivationTokenJpaEntity toJpaEntity(AccountActivationToken accountActivationToken) {
        AccountActivationTokenJpaEntity entity = new AccountActivationTokenJpaEntity(
                accountActivationToken.getUserId(),
                accountActivationToken.getTokenHash(),
                accountActivationToken.getExpiresAt()
        );
        if (accountActivationToken.getConsumedAt() != null) {
            entity.consume(accountActivationToken.getConsumedAt());
        }
        return entity;
    }

    public void updateJpaEntity(AccountActivationTokenJpaEntity entity, AccountActivationToken accountActivationToken) {
        if (!entity.getTokenHash().equals(accountActivationToken.getTokenHash())
                || !entity.getExpiresAt().equals(accountActivationToken.getExpiresAt())) {
            entity.replace(accountActivationToken.getTokenHash(), accountActivationToken.getExpiresAt());
        }
        if (accountActivationToken.getConsumedAt() != null) {
            entity.consume(accountActivationToken.getConsumedAt());
        }
    }

    public AccountActivationToken toDomain(AccountActivationTokenJpaEntity entity) {
        return AccountActivationToken.rehydrate(
                entity.getId(),
                entity.getUserId(),
                entity.getTokenHash(),
                entity.getExpiresAt(),
                entity.getConsumedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
