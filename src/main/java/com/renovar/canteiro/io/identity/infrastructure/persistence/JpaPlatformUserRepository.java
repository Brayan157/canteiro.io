package com.renovar.canteiro.io.identity.infrastructure.persistence;

import com.renovar.canteiro.io.identity.domain.PlatformUser;
import com.renovar.canteiro.io.identity.domain.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlatformUserRepository implements PlatformUserRepository {

    private final PlatformUserJpaRepository platformUserJpaRepository;
    private final PlatformUserPersistenceMapper platformUserPersistenceMapper;

    @Override
    public PlatformUser save(PlatformUser platformUser) {
        if (platformUser.getId() != null) {
            throw new IllegalStateException("Platform user links are immutable");
        }
        return platformUserPersistenceMapper.toDomain(
                platformUserJpaRepository.save(platformUserPersistenceMapper.toJpaEntity(platformUser))
        );
    }

    @Override
    public Optional<PlatformUser> findByUserId(UUID userId) {
        return platformUserJpaRepository.findByUserId(userId).map(platformUserPersistenceMapper::toDomain);
    }
}
