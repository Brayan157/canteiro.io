package com.renovar.canteiro.io.platform.catalog.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanBundlePriceRepository {

    PlanBundlePrice save(PlanBundlePrice planBundlePrice);

    List<PlanBundlePrice> findByPlanBundleId(UUID planBundleId);

    default Optional<PlanBundlePrice> findPriceEffectiveOn(UUID planBundleId, LocalDate date) {
        return findByPlanBundleId(planBundleId).stream()
                .filter(price -> !price.getValidFrom().isAfter(date))
                .filter(price -> price.getValidUntil() == null || !price.getValidUntil().isBefore(date))
                .findFirst();
    }
}
