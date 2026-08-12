package com.renovar.canteiro.io.platform.support.api.response;

import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.identity.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record PlatformSupportUserResponse(
        UUID id,
        UUID userId,
        String email,
        PlatformUserRole globalRole,
        UserStatus status,
        Instant createdAt
) {
}
