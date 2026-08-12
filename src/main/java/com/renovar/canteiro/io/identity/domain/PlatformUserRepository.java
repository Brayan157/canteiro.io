package com.renovar.canteiro.io.identity.domain;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository {

    PlatformUser save(PlatformUser platformUser);

    Optional<PlatformUser> findByUserId(UUID userId);
}
