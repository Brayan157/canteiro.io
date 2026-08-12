package com.renovar.canteiro.io.platform.catalog.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CatalogPriceQuote(
        Set<UUID> planIds,
        BigDecimal amount,
        LocalDate effectiveDate,
        CatalogPricingSource source,
        UUID planBundleId
) {

    public CatalogPriceQuote {
        planIds = Set.copyOf(planIds);
        if (planIds.isEmpty()) {
            throw new IllegalArgumentException("A price quote requires at least one plan");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("A price quote amount must be greater than or equal to zero");
        }
        if (effectiveDate == null || source == null) {
            throw new IllegalArgumentException("A price quote effective date and source are required");
        }
        if (source == CatalogPricingSource.PLAN_BUNDLE && planBundleId == null) {
            throw new IllegalArgumentException("A bundle quote must identify its plan bundle");
        }
        if (source == CatalogPricingSource.INDIVIDUAL_PLANS && planBundleId != null) {
            throw new IllegalArgumentException("An individual plan quote cannot identify a plan bundle");
        }
    }
}
