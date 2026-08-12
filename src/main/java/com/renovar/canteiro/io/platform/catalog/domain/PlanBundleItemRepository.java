package com.renovar.canteiro.io.platform.catalog.domain;

import java.util.Set;
import java.util.UUID;

public interface PlanBundleItemRepository {

    PlanBundleItem save(PlanBundleItem item);

    Set<UUID> findActivePlanIdsByPlanBundleId(UUID planBundleId);
}
