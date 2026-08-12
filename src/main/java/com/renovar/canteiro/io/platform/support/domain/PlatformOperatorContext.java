package com.renovar.canteiro.io.platform.support.domain;

import com.renovar.canteiro.io.identity.domain.PlatformUserRole;

import java.util.UUID;

public record PlatformOperatorContext(UUID userId, UUID platformUserId, PlatformUserRole globalRole) {

    public PlatformOperatorContext {
        if (userId == null || platformUserId == null || globalRole == null) {
            throw new IllegalArgumentException("Platform operator context is incomplete");
        }
    }
}
