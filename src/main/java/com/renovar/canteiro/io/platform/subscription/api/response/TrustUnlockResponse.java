package com.renovar.canteiro.io.platform.subscription.api.response;

import java.time.Instant;
import java.util.UUID;

public record TrustUnlockResponse(
        UUID id,
        UUID companyId,
        UUID chargeId,
        UUID grantedByUserId,
        String reason,
        Instant startsAt,
        Instant expiresAt
) {
}
