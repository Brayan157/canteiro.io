package com.renovar.canteiro.io.platform.company.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public final class CompanyOnboardingPlanSelection {

    private final UUID id;
    private final UUID companyId;
    private final UUID planId;
    private final Instant selectedAt;
    private final Instant createdAt;

    private CompanyOnboardingPlanSelection(UUID id, UUID companyId, UUID planId, Instant selectedAt, Instant createdAt) {
        this.id = id;
        this.companyId = require(companyId, "Company id");
        this.planId = require(planId, "Plan id");
        this.selectedAt = require(selectedAt, "Selection timestamp");
        this.createdAt = createdAt;
    }

    public static CompanyOnboardingPlanSelection create(UUID companyId, UUID planId, Instant selectedAt) {
        return new CompanyOnboardingPlanSelection(null, companyId, planId, selectedAt, null);
    }

    public static CompanyOnboardingPlanSelection rehydrate(
            UUID id, UUID companyId, UUID planId, Instant selectedAt, Instant createdAt
    ) {
        return new CompanyOnboardingPlanSelection(id, companyId, planId, selectedAt, createdAt);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
