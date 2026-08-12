package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.RefreshToken;
import com.renovar.canteiro.io.identity.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final RefreshTokenPersistenceMapper refreshTokenPersistenceMapper;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        if (refreshToken.getId() == null) {
            return refreshTokenPersistenceMapper.toDomain(
                    refreshTokenJpaRepository.save(refreshTokenPersistenceMapper.toJpaEntity(refreshToken))
            );
        }

        RefreshTokenJpaEntity entity = refreshTokenJpaRepository.findById(refreshToken.getId())
                .orElseThrow(() -> new IllegalStateException("Refresh token must exist before it can be updated"));
        refreshTokenPersistenceMapper.updateJpaEntity(entity, refreshToken);
        return refreshTokenPersistenceMapper.toDomain(refreshTokenJpaRepository.save(entity));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return refreshTokenJpaRepository.findByTokenHash(tokenHash).map(refreshTokenPersistenceMapper::toDomain);
    }

    @Override
    public List<RefreshToken> findByUserId(java.util.UUID userId) {
        return refreshTokenJpaRepository.findByUserId(userId).stream()
                .map(refreshTokenPersistenceMapper::toDomain)
                .toList();
    }
}
