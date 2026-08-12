package com.renovar.canteiro.io.platform.catalog.api.request;

import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePlanFeatureRequest(
        @NotBlank @Size(max = 50) String code,
        @NotNull PlanFeatureType type,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {
}
