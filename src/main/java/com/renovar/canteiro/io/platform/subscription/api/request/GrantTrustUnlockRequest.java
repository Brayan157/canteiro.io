package com.renovar.canteiro.io.platform.subscription.api.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record GrantTrustUnlockRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Future Instant expiresAt
) {
}
