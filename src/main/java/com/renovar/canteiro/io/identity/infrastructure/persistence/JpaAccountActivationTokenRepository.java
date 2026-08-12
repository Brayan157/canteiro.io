package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.AccountActivationToken;
import com.renovar.canteiro.io.identity.domain.AccountActivationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaAccountActivationTokenRepository implements AccountActivationTokenRepository {

    private final AccountActivationTokenJpaRepository accountActivationTokenJpaRepository;
    private final AccountActivationTokenPersistenceMapper accountActivationTokenPersistenceMapper;

    @Override
    public AccountActivationToken save(AccountActivationToken accountActivationToken) {
        if (accountActivationToken.getId() == null) {
            return accountActivationTokenPersistenceMapper.toDomain(
                    accountActivationTokenJpaRepository.save(accountActivationTokenPersistenceMapper.toJpaEntity(accountActivationToken))
            );
        }

        AccountActivationTokenJpaEntity entity = accountActivationTokenJpaRepository.findById(accountActivationToken.getId())
                .orElseThrow(() -> new IllegalStateException("Activation token must exist before it can be updated"));
        accountActivationTokenPersistenceMapper.updateJpaEntity(entity, accountActivationToken);
        return accountActivationTokenPersistenceMapper.toDomain(accountActivationTokenJpaRepository.save(entity));
    }

    @Override
    public Optional<AccountActivationToken> findByTokenHash(String tokenHash) {
        return accountActivationTokenJpaRepository.findByTokenHash(tokenHash)
                .map(accountActivationTokenPersistenceMapper::toDomain);
    }
}
