package com.renovar.canteiro.io.platform.catalog.api;

import com.renovar.canteiro.io.platform.catalog.api.request.AddPlanBundleItemRequest;
import com.renovar.canteiro.io.platform.catalog.api.request.AddPlanFeatureRequest;
import com.renovar.canteiro.io.platform.catalog.api.request.CreateCatalogPriceRequest;
import com.renovar.canteiro.io.platform.catalog.api.request.CreatePlanBundleRequest;
import com.renovar.canteiro.io.platform.catalog.api.request.CreatePlanFeatureRequest;
import com.renovar.canteiro.io.platform.catalog.api.request.CreatePlanRequest;
import com.renovar.canteiro.io.platform.catalog.api.request.UpdateCatalogItemRequest;
import com.renovar.canteiro.io.platform.catalog.api.response.CatalogAssignmentResponse;
import com.renovar.canteiro.io.platform.catalog.api.response.CatalogPriceResponse;
import com.renovar.canteiro.io.platform.catalog.api.response.PlanBundleResponse;
import com.renovar.canteiro.io.platform.catalog.api.response.PlanFeatureResponse;
import com.renovar.canteiro.io.platform.catalog.api.response.PlanResponse;
import com.renovar.canteiro.io.platform.catalog.application.PlatformCatalogManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform/catalog")
@Tag(name = "Platform catalog")
public class PlatformCatalogController {

    private final PlatformCatalogManagementService platformCatalogManagementService;
    private final PlatformCatalogApiMapper platformCatalogApiMapper;

    @GetMapping("/plans")
    @Operation(summary = "Lists plans for the platform owner")
    public List<PlanResponse> findPlans() {
        return platformCatalogManagementService.findPlans().stream().map(platformCatalogApiMapper::toResponse).toList();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a plan as the platform owner")
    public PlanResponse createPlan(@Valid @RequestBody CreatePlanRequest request) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.createPlan(
                request.code(), request.name(), request.description()
        ));
    }

    @PatchMapping("/plans/{planId}")
    @Operation(summary = "Updates a plan as the platform owner")
    public PlanResponse updatePlan(@PathVariable UUID planId, @Valid @RequestBody UpdateCatalogItemRequest request) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.updatePlan(
                planId, request.name(), request.description()
        ));
    }

    @PatchMapping("/plans/{planId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivates a plan as the platform owner")
    public void deactivatePlan(@PathVariable UUID planId) {
        platformCatalogManagementService.deactivatePlan(planId);
    }

    @PostMapping("/plans/{planId}/features")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adds a feature to a plan as the platform owner")
    public CatalogAssignmentResponse addFeatureToPlan(
            @PathVariable UUID planId,
            @Valid @RequestBody AddPlanFeatureRequest request
    ) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.addFeatureToPlan(planId, request.featureId()));
    }

    @PostMapping("/plans/{planId}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adds a price period to a plan as the platform owner")
    public CatalogPriceResponse addPlanPrice(
            @PathVariable UUID planId,
            @Valid @RequestBody CreateCatalogPriceRequest request
    ) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.addPlanPrice(
                planId, request.amount(), request.validFrom(), request.validUntil()
        ));
    }

    @GetMapping("/features")
    @Operation(summary = "Lists plan features for the platform owner")
    public List<PlanFeatureResponse> findFeatures() {
        return platformCatalogManagementService.findFeatures().stream().map(platformCatalogApiMapper::toResponse).toList();
    }

    @PostMapping("/features")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a module or feature as the platform owner")
    public PlanFeatureResponse createFeature(@Valid @RequestBody CreatePlanFeatureRequest request) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.createFeature(
                request.code(), request.type(), request.name(), request.description()
        ));
    }

    @PatchMapping("/features/{featureId}")
    @Operation(summary = "Updates a plan feature as the platform owner")
    public PlanFeatureResponse updateFeature(@PathVariable UUID featureId, @Valid @RequestBody UpdateCatalogItemRequest request) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.updateFeature(
                featureId, request.name(), request.description()
        ));
    }

    @PatchMapping("/features/{featureId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivates a plan feature as the platform owner")
    public void deactivateFeature(@PathVariable UUID featureId) {
        platformCatalogManagementService.deactivateFeature(featureId);
    }

    @GetMapping("/bundles")
    @Operation(summary = "Lists plan bundles for the platform owner")
    public List<PlanBundleResponse> findBundles() {
        return platformCatalogManagementService.findBundles().stream().map(platformCatalogApiMapper::toResponse).toList();
    }

    @PostMapping("/bundles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creates a plan bundle as the platform owner")
    public PlanBundleResponse createBundle(@Valid @RequestBody CreatePlanBundleRequest request) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.createBundle(
                request.code(), request.name(), request.description()
        ));
    }

    @PatchMapping("/bundles/{bundleId}")
    @Operation(summary = "Updates a plan bundle as the platform owner")
    public PlanBundleResponse updateBundle(@PathVariable UUID bundleId, @Valid @RequestBody UpdateCatalogItemRequest request) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.updateBundle(
                bundleId, request.name(), request.description()
        ));
    }

    @PatchMapping("/bundles/{bundleId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivates a plan bundle as the platform owner")
    public void deactivateBundle(@PathVariable UUID bundleId) {
        platformCatalogManagementService.deactivateBundle(bundleId);
    }

    @PostMapping("/bundles/{bundleId}/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adds a plan to a bundle as the platform owner")
    public CatalogAssignmentResponse addPlanToBundle(
            @PathVariable UUID bundleId,
            @Valid @RequestBody AddPlanBundleItemRequest request
    ) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.addPlanToBundle(bundleId, request.planId()));
    }

    @PostMapping("/bundles/{bundleId}/prices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adds a price period to a bundle as the platform owner")
    public CatalogPriceResponse addBundlePrice(
            @PathVariable UUID bundleId,
            @Valid @RequestBody CreateCatalogPriceRequest request
    ) {
        return platformCatalogApiMapper.toResponse(platformCatalogManagementService.addBundlePrice(
                bundleId, request.amount(), request.validFrom(), request.validUntil()
        ));
    }
}
