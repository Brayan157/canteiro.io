package com.renovar.canteiro.io.identity.domain;

import java.util.Optional;
import java.util.UUID;

public interface AccountActivationTokenRepository {

    AccountActivationToken save(AccountActivationToken accountActivationToken);

    Optional<AccountActivationToken> findByTokenHash(String tokenHash);

    Optional<AccountActivationToken> findByUserId(UUID userId);
}
