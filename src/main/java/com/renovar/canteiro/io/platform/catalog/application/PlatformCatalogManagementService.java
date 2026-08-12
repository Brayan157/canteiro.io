package com.renovar.canteiro.io.platform.catalog.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.identity.domain.PlatformUserRole;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundle;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItem;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItemRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeature;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignment;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignmentRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureType;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.domain.PlatformOperatorContext;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformCatalogManagementService {

    private final PlatformOperatorContextHolder platformOperatorContextHolder;
    private final AuditEventRecorder auditEventRecorder;
    private final PlanRepository planRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PlanFeatureAssignmentRepository planFeatureAssignmentRepository;
    private final PlanPriceRepository planPriceRepository;
    private final PlanBundleRepository planBundleRepository;
    private final PlanBundleItemRepository planBundleItemRepository;
    private final PlanBundlePriceRepository planBundlePriceRepository;

    @Transactional
    public Plan createPlan(String code, String name, String description) {
        requireOwner();
        if (planRepository.findByCode(code).isPresent()) {
            throw conflict("Plan code is already in use");
        }
        Plan plan = planRepository.save(Plan.create(code, name, description));
        record(AuditAction.CREATE, "Plan", plan.getId(), null, planData(plan));
        return plan;
    }

    @Transactional
    public Plan updatePlan(UUID planId, String name, String description) {
        requireOwner();
        Plan plan = requirePlan(planId);
        Map<String, Object> before = planData(plan);
        plan.update(name, description);
        Plan savedPlan = planRepository.save(plan);
        record(AuditAction.UPDATE, "Plan", savedPlan.getId(), before, planData(savedPlan));
        return savedPlan;
    }

    @Transactional
    public void deactivatePlan(UUID planId) {
        requireOwner();
        Plan plan = requirePlan(planId);
        Map<String, Object> before = planData(plan);
        plan.deactivate();
        Plan savedPlan = planRepository.save(plan);
        record(AuditAction.DEACTIVATE, "Plan", savedPlan.getId(), before, planData(savedPlan));
    }

    @Transactional
    public PlanFeature createFeature(String code, PlanFeatureType type, String name, String description) {
        requireOwner();
        if (planFeatureRepository.findByCode(code).isPresent()) {
            throw conflict("Plan feature code is already in use");
        }
        PlanFeature feature = planFeatureRepository.save(PlanFeature.create(code, type, name, description));
        record(AuditAction.CREATE, "PlanFeature", feature.getId(), null, featureData(feature));
        return feature;
    }

    @Transactional
    public PlanFeature updateFeature(UUID featureId, String name, String description) {
        requireOwner();
        PlanFeature feature = requireFeature(featureId);
        Map<String, Object> before = featureData(feature);
        feature.update(name, description);
        PlanFeature savedFeature = planFeatureRepository.save(feature);
        record(AuditAction.UPDATE, "PlanFeature", savedFeature.getId(), before, featureData(savedFeature));
        return savedFeature;
    }

    @Transactional
    public void deactivateFeature(UUID featureId) {
        requireOwner();
        PlanFeature feature = requireFeature(featureId);
        Map<String, Object> before = featureData(feature);
        feature.deactivate();
        PlanFeature savedFeature = planFeatureRepository.save(feature);
        record(AuditAction.DEACTIVATE, "PlanFeature", savedFeature.getId(), before, featureData(savedFeature));
    }

    @Transactional
    public PlanFeatureAssignment addFeatureToPlan(UUID planId, UUID featureId) {
        requireOwner();
        requirePlan(planId);
        requireFeature(featureId);
        PlanFeatureAssignment assignment = planFeatureAssignmentRepository.save(PlanFeatureAssignment.create(planId, featureId));
        record(AuditAction.CREATE, "PlanFeatureAssignment", assignment.getId(), null, assignmentData(assignment));
        return assignment;
    }

    @Transactional
    public PlanPrice addPlanPrice(UUID planId, BigDecimal amount, LocalDate validFrom, LocalDate validUntil) {
        requireOwner();
        requirePlan(planId);
        PlanPrice candidate = PlanPrice.create(planId, amount, validFrom, validUntil);
        rejectPlanPriceOverlap(planId, candidate.getValidFrom(), candidate.getValidUntil());
        PlanPrice price = planPriceRepository.save(candidate);
        record(AuditAction.CREATE, "PlanPrice", price.getId(), null, priceData(price));
        return price;
    }

    @Transactional
    public PlanBundle createBundle(String code, String name, String description) {
        requireOwner();
        if (planBundleRepository.findByCode(code).isPresent()) {
            throw conflict("Plan bundle code is already in use");
        }
        PlanBundle bundle = planBundleRepository.save(PlanBundle.create(code, name, description));
        record(AuditAction.CREATE, "PlanBundle", bundle.getId(), null, bundleData(bundle));
        return bundle;
    }

    @Transactional
    public PlanBundle updateBundle(UUID bundleId, String name, String description) {
        requireOwner();
        PlanBundle bundle = requireBundle(bundleId);
        Map<String, Object> before = bundleData(bundle);
        bundle.update(name, description);
        PlanBundle savedBundle = planBundleRepository.save(bundle);
        record(AuditAction.UPDATE, "PlanBundle", savedBundle.getId(), before, bundleData(savedBundle));
        return savedBundle;
    }

    @Transactional
    public void deactivateBundle(UUID bundleId) {
        requireOwner();
        PlanBundle bundle = requireBundle(bundleId);
        Map<String, Object> before = bundleData(bundle);
        bundle.deactivate();
        PlanBundle savedBundle = planBundleRepository.save(bundle);
        record(AuditAction.DEACTIVATE, "PlanBundle", savedBundle.getId(), before, bundleData(savedBundle));
    }

    @Transactional
    public PlanBundleItem addPlanToBundle(UUID bundleId, UUID planId) {
        requireOwner();
        requireBundle(bundleId);
        requirePlan(planId);
        if (planBundleItemRepository.findActivePlanIdsByPlanBundleId(bundleId).contains(planId)) {
            throw conflict("Plan is already active in the bundle");
        }
        PlanBundleItem item = planBundleItemRepository.save(PlanBundleItem.create(bundleId, planId));
        record(AuditAction.CREATE, "PlanBundleItem", item.getId(), null, bundleItemData(item));
        return item;
    }

    @Transactional
    public PlanBundlePrice addBundlePrice(UUID bundleId, BigDecimal amount, LocalDate validFrom, LocalDate validUntil) {
        requireOwner();
        requireBundle(bundleId);
        PlanBundlePrice candidate = PlanBundlePrice.create(bundleId, amount, validFrom, validUntil);
        rejectBundlePriceOverlap(bundleId, candidate.getValidFrom(), candidate.getValidUntil());
        PlanBundlePrice price = planBundlePriceRepository.save(candidate);
        record(AuditAction.CREATE, "PlanBundlePrice", price.getId(), null, bundlePriceData(price));
        return price;
    }

    @Transactional(readOnly = true)
    public List<Plan> findPlans() {
        requireOwner();
        return planRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PlanFeature> findFeatures() {
        requireOwner();
        return planFeatureRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PlanBundle> findBundles() {
        requireOwner();
        return planBundleRepository.findAll();
    }

    private void requireOwner() {
        PlatformOperatorContext operator = platformOperatorContextHolder.currentOperator()
                .orElseThrow(() -> new AccessDeniedException("A platform owner is required"));
        if (operator.globalRole() != PlatformUserRole.PLATFORM_OWNER) {
            throw new AccessDeniedException("A platform owner is required");
        }
    }

    private Plan requirePlan(UUID planId) {
        return planRepository.findById(planId).orElseThrow(() -> notFound("Plan was not found"));
    }

    private PlanFeature requireFeature(UUID featureId) {
        return planFeatureRepository.findById(featureId).orElseThrow(() -> notFound("Plan feature was not found"));
    }

    private PlanBundle requireBundle(UUID bundleId) {
        return planBundleRepository.findById(bundleId).orElseThrow(() -> notFound("Plan bundle was not found"));
    }

    private void rejectPlanPriceOverlap(UUID planId, LocalDate validFrom, LocalDate validUntil) {
        if (planPriceRepository.findByPlanId(planId).stream().anyMatch(price -> overlaps(
                price.getValidFrom(), price.getValidUntil(), validFrom, validUntil))) {
            throw conflict("Plan price validity overlaps an existing price");
        }
    }

    private void rejectBundlePriceOverlap(UUID bundleId, LocalDate validFrom, LocalDate validUntil) {
        if (planBundlePriceRepository.findByPlanBundleId(bundleId).stream().anyMatch(price -> overlaps(
                price.getValidFrom(), price.getValidUntil(), validFrom, validUntil))) {
            throw conflict("Plan bundle price validity overlaps an existing price");
        }
    }

    private boolean overlaps(LocalDate firstFrom, LocalDate firstUntil, LocalDate secondFrom, LocalDate secondUntil) {
        return (firstUntil == null || !firstUntil.isBefore(secondFrom))
                && (secondUntil == null || !secondUntil.isBefore(firstFrom));
    }

    private ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, detail);
    }

    private ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, detail);
    }

    private void record(AuditAction action, String entityType, UUID entityId, Map<String, Object> before, Map<String, Object> after) {
        auditEventRecorder.recordDirectAction(AuditModule.PLATFORM, action, entityType, entityId, before, after, Map.of());
    }

    private Map<String, Object> planData(Plan plan) {
        return data("code", plan.getCode(), "name", plan.getName(), "description", plan.getDescription(), "active", plan.isActive());
    }

    private Map<String, Object> featureData(PlanFeature feature) {
        return data("code", feature.getCode(), "type", feature.getType().name(), "name", feature.getName(),
                "description", feature.getDescription(), "active", feature.isActive());
    }

    private Map<String, Object> assignmentData(PlanFeatureAssignment assignment) {
        return data("planId", assignment.getPlanId().toString(), "planFeatureId", assignment.getPlanFeatureId().toString(),
                "active", assignment.isActive());
    }

    private Map<String, Object> priceData(PlanPrice price) {
        return data("planId", price.getPlanId().toString(), "amount", price.getAmount().toPlainString(),
                "validFrom", price.getValidFrom().toString(), "validUntil", value(price.getValidUntil()));
    }

    private Map<String, Object> bundleData(PlanBundle bundle) {
        return data("code", bundle.getCode(), "name", bundle.getName(), "description", bundle.getDescription(),
                "active", bundle.isActive());
    }

    private Map<String, Object> bundleItemData(PlanBundleItem item) {
        return data("planBundleId", item.getPlanBundleId().toString(), "planId", item.getPlanId().toString(),
                "active", item.isActive());
    }

    private Map<String, Object> bundlePriceData(PlanBundlePrice price) {
        return data("planBundleId", price.getPlanBundleId().toString(), "amount", price.getAmount().toPlainString(),
                "validFrom", price.getValidFrom().toString(), "validUntil", value(price.getValidUntil()));
    }

    private Map<String, Object> data(Object... values) {
        Map<String, Object> data = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            data.put((String) values[index], values[index + 1]);
        }
        return data;
    }

    private String value(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
