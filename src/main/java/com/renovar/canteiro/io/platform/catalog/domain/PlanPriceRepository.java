package com.renovar.canteiro.io.platform.catalog.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PlanPriceRepository {

    PlanPrice save(PlanPrice planPrice);

    List<PlanPrice> findByPlanId(UUID planId);

    default PlanPrice requirePriceEffectiveOn(UUID planId, LocalDate date) {
        return findByPlanId(planId).stream()
                .filter(price -> !price.getValidFrom().isAfter(date))
                .filter(price -> price.getValidUntil() == null || !price.getValidUntil().isBefore(date))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No plan price is effective on the requested date"));
    }
}
