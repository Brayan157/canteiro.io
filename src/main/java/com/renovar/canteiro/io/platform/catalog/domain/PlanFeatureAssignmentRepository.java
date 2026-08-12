package com.renovar.canteiro.io.platform.catalog.domain;

import java.util.List;
import java.util.UUID;

public interface PlanFeatureAssignmentRepository {

    PlanFeatureAssignment save(PlanFeatureAssignment assignment);

    List<PlanFeatureAssignment> findActiveByPlanId(UUID planId);
}
