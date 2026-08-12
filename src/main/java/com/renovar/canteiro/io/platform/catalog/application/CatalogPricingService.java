package com.renovar.canteiro.io.platform.catalog.application;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundle;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItemRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogPricingService {

    private final PlanRepository planRepository;
    private final PlanPriceRepository planPriceRepository;
    private final PlanBundleRepository planBundleRepository;
    private final PlanBundleItemRepository planBundleItemRepository;
    private final PlanBundlePriceRepository planBundlePriceRepository;

    @Transactional(readOnly = true)
    public CatalogPriceQuote quote(Collection<UUID> requestedPlanIds, LocalDate effectiveDate) {
        Set<UUID> planIds = requirePlanIds(requestedPlanIds);
        LocalDate priceDate = requireEffectiveDate(effectiveDate);
        requireActivePlans(planIds);

        List<PlanBundle> matchingBundles = planBundleRepository.findAllActive().stream()
                .filter(bundle -> planBundleItemRepository.findActivePlanIdsByPlanBundleId(bundle.getId()).equals(planIds))
                .filter(bundle -> planBundlePriceRepository.findPriceEffectiveOn(bundle.getId(), priceDate).isPresent())
                .toList();
        if (matchingBundles.size() > 1) {
            throw new IllegalStateException("More than one active plan bundle matches the requested plan combination");
        }
        if (matchingBundles.size() == 1) {
            PlanBundle bundle = matchingBundles.getFirst();
            PlanBundlePrice price = planBundlePriceRepository.findPriceEffectiveOn(bundle.getId(), priceDate)
                    .orElseThrow();
            return new CatalogPriceQuote(planIds, price.getAmount(), priceDate, CatalogPricingSource.PLAN_BUNDLE, bundle.getId());
        }

        BigDecimal total = planIds.stream()
                .map(planId -> planPriceRepository.requirePriceEffectiveOn(planId, priceDate).getAmount())
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new CatalogPriceQuote(planIds, total, priceDate, CatalogPricingSource.INDIVIDUAL_PLANS, null);
    }

    private Set<UUID> requirePlanIds(Collection<UUID> requestedPlanIds) {
        if (requestedPlanIds == null || requestedPlanIds.isEmpty() || requestedPlanIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("At least one plan id is required");
        }
        Set<UUID> planIds = Set.copyOf(requestedPlanIds);
        if (planIds.size() != requestedPlanIds.size()) {
            throw new IllegalArgumentException("A plan can be selected only once in a price quote");
        }
        return planIds;
    }

    private LocalDate requireEffectiveDate(LocalDate effectiveDate) {
        if (effectiveDate == null) {
            throw new IllegalArgumentException("A price quote effective date is required");
        }
        return effectiveDate;
    }

    private void requireActivePlans(Set<UUID> planIds) {
        for (UUID planId : planIds) {
            Plan plan = planRepository.findById(planId)
                    .orElseThrow(() -> new IllegalArgumentException("Requested plan does not exist"));
            if (!plan.isActive()) {
                throw new IllegalArgumentException("Requested plan is inactive");
            }
        }
    }
}
