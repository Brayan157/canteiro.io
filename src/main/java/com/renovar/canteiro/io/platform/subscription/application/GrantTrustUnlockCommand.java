package com.renovar.canteiro.io.platform.subscription.application;

import java.time.Instant;
import java.util.UUID;

public record GrantTrustUnlockCommand(UUID chargeId, String reason, Instant expiresAt) {
}
