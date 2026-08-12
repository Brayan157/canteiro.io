package com.renovar.canteiro.io.platform.support.domain;

import com.renovar.canteiro.io.identity.domain.PlatformUserRole;

import java.util.UUID;

public record SupportTargetContext(
        UUID operatorUserId,
        UUID platformUserId,
        PlatformUserRole globalRole,
        UUID targetCompanyId
) {

    public SupportTargetContext {
        if (operatorUserId == null || platformUserId == null || globalRole == null || targetCompanyId == null) {
            throw new IllegalArgumentException("Support target context is incomplete");
        }
    }
}
