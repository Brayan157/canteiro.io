package com.renovar.canteiro.io.identity.domain;

import java.util.Optional;

public interface AccountActivationTokenRepository {

    AccountActivationToken save(AccountActivationToken accountActivationToken);

    Optional<AccountActivationToken> findByTokenHash(String tokenHash);
}
