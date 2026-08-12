package com.renovar.canteiro.io.platform.catalog.api;

import com.renovar.canteiro.io.platform.catalog.api.response.CatalogAssignmentResponse;
import com.renovar.canteiro.io.platform.catalog.api.response.CatalogPriceResponse;
import com.renovar.canteiro.io.platform.catalog.api.response.PlanBundleResponse;
import com.renovar.canteiro.io.platform.catalog.api.response.PlanFeatureResponse;
import com.renovar.canteiro.io.platform.catalog.api.response.PlanResponse;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundle;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItem;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeature;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignment;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import org.springframework.stereotype.Component;

@Component
public class PlatformCatalogApiMapper {

    public PlanResponse toResponse(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), plan.isActive(),
                plan.getCreatedAt(), plan.getUpdatedAt());
    }

    public PlanFeatureResponse toResponse(PlanFeature feature) {
        return new PlanFeatureResponse(feature.getId(), feature.getCode(), feature.getType(), feature.getName(),
                feature.getDescription(), feature.isActive(), feature.getCreatedAt(), feature.getUpdatedAt());
    }

    public PlanBundleResponse toResponse(PlanBundle bundle) {
        return new PlanBundleResponse(bundle.getId(), bundle.getCode(), bundle.getName(), bundle.getDescription(),
                bundle.isActive(), bundle.getCreatedAt(), bundle.getUpdatedAt());
    }

    public CatalogAssignmentResponse toResponse(PlanFeatureAssignment assignment) {
        return new CatalogAssignmentResponse(assignment.getId(), assignment.getPlanId(), assignment.getPlanFeatureId(),
                assignment.isActive(), assignment.getCreatedAt(), assignment.getUpdatedAt());
    }

    public CatalogAssignmentResponse toResponse(PlanBundleItem item) {
        return new CatalogAssignmentResponse(item.getId(), item.getPlanBundleId(), item.getPlanId(), item.isActive(),
                item.getCreatedAt(), item.getUpdatedAt());
    }

    public CatalogPriceResponse toResponse(PlanPrice price) {
        return new CatalogPriceResponse(price.getId(), price.getPlanId(), price.getAmount(), price.getValidFrom(),
                price.getValidUntil(), price.getCreatedAt(), price.getUpdatedAt());
    }

    public CatalogPriceResponse toResponse(PlanBundlePrice price) {
        return new CatalogPriceResponse(price.getId(), price.getPlanBundleId(), price.getAmount(), price.getValidFrom(),
                price.getValidUntil(), price.getCreatedAt(), price.getUpdatedAt());
    }
}
