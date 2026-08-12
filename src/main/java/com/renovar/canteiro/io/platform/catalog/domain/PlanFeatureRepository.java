package com.renovar.canteiro.io.platform.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanFeatureRepository {

    PlanFeature save(PlanFeature planFeature);

    Optional<PlanFeature> findById(UUID id);

    Optional<PlanFeature> findByCode(String code);

    List<PlanFeature> findAll();
}
