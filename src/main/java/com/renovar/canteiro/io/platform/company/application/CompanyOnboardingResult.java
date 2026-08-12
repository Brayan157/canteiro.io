package com.renovar.canteiro.io.platform.company.application;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;

import java.util.List;
import java.util.UUID;

public record CompanyOnboardingResult(
        UUID companyId,
        UUID ownerUserId,
        String ownerEmail,
        List<UUID> selectedPlanIds,
        CatalogPriceQuote priceQuote
) {
}
