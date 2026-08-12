package com.renovar.canteiro.io.platform.company.api;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CompanyOnboardingResponse(
        UUID companyId,
        UUID ownerUserId,
        String ownerEmail,
        List<UUID> selectedPlanIds,
        BigDecimal quotedAmount,
        LocalDate priceEffectiveDate,
        CatalogPricingSource pricingSource,
        UUID planBundleId
) {
}
