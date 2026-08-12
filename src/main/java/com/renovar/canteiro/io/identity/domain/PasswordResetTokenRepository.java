package com.renovar.canteiro.io.identity.domain;

import java.util.Optional;

public interface PasswordResetTokenRepository {

    PasswordResetToken save(PasswordResetToken passwordResetToken);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
