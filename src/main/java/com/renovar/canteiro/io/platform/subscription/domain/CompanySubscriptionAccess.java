package com.renovar.canteiro.io.platform.subscription.domain;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class CompanySubscriptionAccess {

    private final UUID companyId;
    private SubscriptionAccessLevel accessLevel;
    private UUID restrictionChargeId;
    private LocalDate effectiveOn;
    private final Instant createdAt;
    private final Instant updatedAt;

    private CompanySubscriptionAccess(
            UUID companyId,
            SubscriptionAccessLevel accessLevel,
            UUID restrictionChargeId,
            LocalDate effectiveOn,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.companyId = require(companyId, "Company subscription access company is required");
        this.accessLevel = require(accessLevel, "Company subscription access level is required");
        this.restrictionChargeId = restrictionChargeId;
        this.effectiveOn = require(effectiveOn, "Company subscription access effective date is required");
        requireRestriction(accessLevel, restrictionChargeId);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CompanySubscriptionAccess create(
            UUID companyId,
            SubscriptionAccessLevel accessLevel,
            UUID restrictionChargeId,
            LocalDate effectiveOn
    ) {
        return new CompanySubscriptionAccess(
                companyId, accessLevel, restrictionChargeId, effectiveOn, null, null
        );
    }

    public static CompanySubscriptionAccess rehydrate(
            UUID companyId,
            SubscriptionAccessLevel accessLevel,
            UUID restrictionChargeId,
            LocalDate effectiveOn,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new CompanySubscriptionAccess(
                companyId, accessLevel, restrictionChargeId, effectiveOn, createdAt, updatedAt
        );
    }

    public boolean update(
            SubscriptionAccessLevel newAccessLevel,
            UUID newRestrictionChargeId,
            LocalDate newEffectiveOn
    ) {
        require(newAccessLevel, "Company subscription access level is required");
        require(newEffectiveOn, "Company subscription access effective date is required");
        requireRestriction(newAccessLevel, newRestrictionChargeId);
        if (accessLevel == newAccessLevel
                && java.util.Objects.equals(restrictionChargeId, newRestrictionChargeId)) {
            return false;
        }
        accessLevel = newAccessLevel;
        restrictionChargeId = newRestrictionChargeId;
        effectiveOn = newEffectiveOn;
        return true;
    }

    private static void requireRestriction(SubscriptionAccessLevel accessLevel, UUID restrictionChargeId) {
        if (accessLevel == SubscriptionAccessLevel.FULL && restrictionChargeId != null) {
            throw new IllegalArgumentException("Full company access cannot identify a restriction charge");
        }
        if (accessLevel != SubscriptionAccessLevel.FULL && restrictionChargeId == null) {
            throw new IllegalArgumentException("Restricted company access requires a restriction charge");
        }
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
