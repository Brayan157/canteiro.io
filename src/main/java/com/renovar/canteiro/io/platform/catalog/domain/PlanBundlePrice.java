package com.renovar.canteiro.io.platform.catalog.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class PlanBundlePrice {

    private final UUID id;
    private final UUID planBundleId;
    private final BigDecimal amount;
    private final LocalDate validFrom;
    private LocalDate validUntil;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PlanBundlePrice(
            UUID id,
            UUID planBundleId,
            BigDecimal amount,
            LocalDate validFrom,
            LocalDate validUntil,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.planBundleId = requirePlanBundleId(planBundleId);
        this.amount = requireAmount(amount);
        this.validFrom = requireValidFrom(validFrom);
        this.validUntil = requireValidUntil(validFrom, validUntil);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlanBundlePrice create(
            UUID planBundleId,
            BigDecimal amount,
            LocalDate validFrom,
            LocalDate validUntil
    ) {
        return new PlanBundlePrice(null, planBundleId, amount, validFrom, validUntil, null, null);
    }

    public static PlanBundlePrice rehydrate(
            UUID id,
            UUID planBundleId,
            BigDecimal amount,
            LocalDate validFrom,
            LocalDate validUntil,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PlanBundlePrice(id, planBundleId, amount, validFrom, validUntil, createdAt, updatedAt);
    }

    public void endOn(LocalDate validUntil) {
        this.validUntil = requireValidUntil(validFrom, validUntil);
    }

    private static UUID requirePlanBundleId(UUID planBundleId) {
        if (planBundleId == null) {
            throw new IllegalArgumentException("Plan bundle price bundle is required");
        }
        return planBundleId;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Plan bundle price amount must be greater than or equal to zero");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Plan bundle price amount must have at most two decimal places", exception);
        }
    }

    private static LocalDate requireValidFrom(LocalDate validFrom) {
        if (validFrom == null) {
            throw new IllegalArgumentException("Plan bundle price valid from date is required");
        }
        return validFrom;
    }

    private static LocalDate requireValidUntil(LocalDate validFrom, LocalDate validUntil) {
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("Plan bundle price valid until date cannot be before the valid from date");
        }
        return validUntil;
    }
}
