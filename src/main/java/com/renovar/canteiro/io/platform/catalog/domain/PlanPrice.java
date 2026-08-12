package com.renovar.canteiro.io.platform.catalog.domain;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class PlanPrice {

    private final UUID id;
    private final UUID planId;
    private final BigDecimal amount;
    private final LocalDate validFrom;
    private LocalDate validUntil;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PlanPrice(
            UUID id,
            UUID planId,
            BigDecimal amount,
            LocalDate validFrom,
            LocalDate validUntil,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.planId = requirePlanId(planId);
        this.amount = requireAmount(amount);
        this.validFrom = requireValidFrom(validFrom);
        this.validUntil = requireValidUntil(validFrom, validUntil);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlanPrice create(UUID planId, BigDecimal amount, LocalDate validFrom, LocalDate validUntil) {
        return new PlanPrice(null, planId, amount, validFrom, validUntil, null, null);
    }

    public static PlanPrice rehydrate(
            UUID id,
            UUID planId,
            BigDecimal amount,
            LocalDate validFrom,
            LocalDate validUntil,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PlanPrice(id, planId, amount, validFrom, validUntil, createdAt, updatedAt);
    }

    public void endOn(LocalDate validUntil) {
        this.validUntil = requireValidUntil(validFrom, validUntil);
    }

    private static UUID requirePlanId(UUID planId) {
        if (planId == null) {
            throw new IllegalArgumentException("Plan price plan is required");
        }
        return planId;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Plan price amount must be greater than or equal to zero");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Plan price amount must have at most two decimal places", exception);
        }
    }

    private static LocalDate requireValidFrom(LocalDate validFrom) {
        if (validFrom == null) {
            throw new IllegalArgumentException("Plan price valid from date is required");
        }
        return validFrom;
    }

    private static LocalDate requireValidUntil(LocalDate validFrom, LocalDate validUntil) {
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("Plan price valid until date cannot be before the valid from date");
        }
        return validUntil;
    }
}
