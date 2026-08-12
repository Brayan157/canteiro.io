package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.PasswordResetToken;
import com.renovar.canteiro.io.identity.domain.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JpaPasswordResetTokenRepository implements PasswordResetTokenRepository {

    private final PasswordResetTokenJpaRepository passwordResetTokenJpaRepository;
    private final PasswordResetTokenPersistenceMapper passwordResetTokenPersistenceMapper;

    @Override
    public PasswordResetToken save(PasswordResetToken passwordResetToken) {
        if (passwordResetToken.getId() == null) {
            return passwordResetTokenPersistenceMapper.toDomain(
                    passwordResetTokenJpaRepository.save(passwordResetTokenPersistenceMapper.toJpaEntity(passwordResetToken))
            );
        }

        PasswordResetTokenJpaEntity entity = passwordResetTokenJpaRepository.findById(passwordResetToken.getId())
                .orElseThrow(() -> new IllegalStateException("Password reset token must exist before it can be updated"));
        passwordResetTokenPersistenceMapper.updateJpaEntity(entity, passwordResetToken);
        return passwordResetTokenPersistenceMapper.toDomain(passwordResetTokenJpaRepository.save(entity));
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return passwordResetTokenJpaRepository.findByTokenHash(tokenHash).map(passwordResetTokenPersistenceMapper::toDomain);
    }
}
