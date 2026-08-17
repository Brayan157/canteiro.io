package com.renovar.canteiro.io.platform.subscription.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class TrustUnlock {

    private static final int MAX_REASON_LENGTH = 500;

    private final UUID id;
    private final UUID companyId;
    private final UUID chargeId;
    private final UUID grantedByUserId;
    private final String reason;
    private final Instant startsAt;
    private final Instant expiresAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TrustUnlock(
            UUID id,
            UUID companyId,
            UUID chargeId,
            UUID grantedByUserId,
            String reason,
            Instant startsAt,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.companyId = require(companyId, "Trust unlock company is required");
        this.chargeId = require(chargeId, "Trust unlock charge is required");
        this.grantedByUserId = require(grantedByUserId, "Trust unlock author is required");
        this.reason = requireReason(reason);
        this.startsAt = require(startsAt, "Trust unlock start is required");
        this.expiresAt = require(expiresAt, "Trust unlock expiration is required");
        if (!expiresAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Trust unlock expiration must be after its start");
        }
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static TrustUnlock create(
            UUID companyId,
            UUID chargeId,
            UUID grantedByUserId,
            String reason,
            Instant startsAt,
            Instant expiresAt
    ) {
        return new TrustUnlock(
                null, companyId, chargeId, grantedByUserId, reason, startsAt, expiresAt, null, null
        );
    }

    public static TrustUnlock rehydrate(
            UUID id,
            UUID companyId,
            UUID chargeId,
            UUID grantedByUserId,
            String reason,
            Instant startsAt,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new TrustUnlock(
                require(id, "Trust unlock id is required"), companyId, chargeId, grantedByUserId, reason,
                startsAt, expiresAt, createdAt, updatedAt
        );
    }

    public boolean isActiveAt(Instant instant) {
        Instant currentInstant = require(instant, "Trust unlock assessment instant is required");
        return !currentInstant.isBefore(startsAt) && currentInstant.isBefore(expiresAt);
    }

    private static String requireReason(String value) {
        if (value == null || value.isBlank() || value.trim().length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Trust unlock reason must have between 1 and 500 characters");
        }
        return value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
