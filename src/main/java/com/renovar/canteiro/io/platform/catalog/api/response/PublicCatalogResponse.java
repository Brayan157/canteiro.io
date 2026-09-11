package com.renovar.canteiro.io.platform.catalog.api.response;

import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PublicCatalogResponse(
        LocalDate effectiveOn,
        List<PublicPlanResponse> plans,
        List<PublicPlanBundleResponse> bundles
) {

    public record PublicPlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            BigDecimal amount,
            LocalDate validFrom,
            LocalDate validUntil,
            List<PublicFeatureResponse> features
    ) {
    }

    public record PublicPlanBundleResponse(
            UUID id,
            String code,
            String name,
            String description,
            BigDecimal amount,
            LocalDate validFrom,
            LocalDate validUntil,
            List<PublicBundlePlanResponse> plans
    ) {
    }

    public record PublicBundlePlanResponse(UUID id, String code, String name) {
    }

    public record PublicFeatureResponse(String code, PlanFeatureType type, String name, String description) {
    }
}
