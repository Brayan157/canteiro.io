package com.renovar.canteiro.io.platform.catalog.application;

import com.renovar.canteiro.io.platform.catalog.api.response.PublicCatalogResponse;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundle;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItemRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeature;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignmentRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class PublicCatalogQueryService {

    private final PlanRepository planRepository;
    private final PlanPriceRepository planPriceRepository;
    private final PlanFeatureAssignmentRepository planFeatureAssignmentRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final PlanBundleRepository planBundleRepository;
    private final PlanBundleItemRepository planBundleItemRepository;
    private final PlanBundlePriceRepository planBundlePriceRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PublicCatalogResponse currentCatalog() {
        LocalDate effectiveOn = LocalDate.now(clock);
        Map<UUID, Plan> activePlansById = planRepository.findAll().stream()
                .filter(Plan::isActive)
                .collect(java.util.stream.Collectors.toMap(Plan::getId, Function.identity()));
        List<PublicCatalogResponse.PublicPlanResponse> plans = activePlansById.values().stream()
                .map(plan -> toPublicPlan(plan, effectiveOn))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(PublicCatalogResponse.PublicPlanResponse::name))
                .toList();
        List<PublicCatalogResponse.PublicPlanBundleResponse> bundles = planBundleRepository.findAllActive().stream()
                .map(bundle -> toPublicBundle(bundle, activePlansById, effectiveOn))
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparing(PublicCatalogResponse.PublicPlanBundleResponse::name))
                .toList();
        return new PublicCatalogResponse(effectiveOn, plans, bundles);
    }

    private java.util.Optional<PublicCatalogResponse.PublicPlanResponse> toPublicPlan(
            Plan plan,
            LocalDate effectiveOn
    ) {
        return findPlanPrice(plan.getId(), effectiveOn).map(price -> new PublicCatalogResponse.PublicPlanResponse(
                plan.getId(), plan.getCode(), plan.getName(), plan.getDescription(), price.getAmount(),
                price.getValidFrom(), price.getValidUntil(), activeFeatures(plan.getId())
        ));
    }

    private java.util.Optional<PublicCatalogResponse.PublicPlanBundleResponse> toPublicBundle(
            PlanBundle bundle,
            Map<UUID, Plan> activePlansById,
            LocalDate effectiveOn
    ) {
        Set<UUID> planIds = planBundleItemRepository.findActivePlanIdsByPlanBundleId(bundle.getId());
        if (planIds.isEmpty() || !activePlansById.keySet().containsAll(planIds)) {
            return java.util.Optional.empty();
        }
        return planBundlePriceRepository.findPriceEffectiveOn(bundle.getId(), effectiveOn).map(price ->
                new PublicCatalogResponse.PublicPlanBundleResponse(
                        bundle.getId(), bundle.getCode(), bundle.getName(), bundle.getDescription(),
                        price.getAmount(), price.getValidFrom(), price.getValidUntil(),
                        planIds.stream()
                                .map(activePlansById::get)
                                .sorted(Comparator.comparing(Plan::getName))
                                .map(plan -> new PublicCatalogResponse.PublicBundlePlanResponse(
                                        plan.getId(), plan.getCode(), plan.getName()
                                ))
                                .toList()
                )
        );
    }

    private java.util.Optional<PlanPrice> findPlanPrice(UUID planId, LocalDate effectiveOn) {
        return planPriceRepository.findByPlanId(planId).stream()
                .filter(price -> !price.getValidFrom().isAfter(effectiveOn))
                .filter(price -> price.getValidUntil() == null || !price.getValidUntil().isBefore(effectiveOn))
                .findFirst();
    }

    private List<PublicCatalogResponse.PublicFeatureResponse> activeFeatures(UUID planId) {
        return planFeatureAssignmentRepository.findActiveByPlanId(planId).stream()
                .map(assignment -> planFeatureRepository.findById(assignment.getPlanFeatureId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .filter(PlanFeature::isActive)
                .sorted(Comparator.comparing(PlanFeature::getName))
                .map(feature -> new PublicCatalogResponse.PublicFeatureResponse(
                        feature.getCode(), feature.getType(), feature.getName(), feature.getDescription()
                ))
                .toList();
    }
}
