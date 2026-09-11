package com.renovar.canteiro.io.platform.subscription.api.response;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "Overdue platform charge and its trust unlock availability")
public record PlatformChargeTrustUnlockResponse(
        UUID id,
        CompanyResponse company,
        @Schema(type = "string", example = "99.90", description = "Canonical decimal amount") String amount,
        LocalDate dueDate,
        PlatformChargeStatus status,
        long trustUnlocksUsed,
        int trustUnlockLimit,
        long remainingTrustUnlocks,
        ActiveTrustUnlockResponse activeTrustUnlock
) {
    public record CompanyResponse(UUID id, String corporateName, String tradeName) {
    }

    public record ActiveTrustUnlockResponse(Instant expiresAt) {
    }
}
