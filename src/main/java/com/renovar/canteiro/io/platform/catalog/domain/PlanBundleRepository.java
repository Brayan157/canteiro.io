package com.renovar.canteiro.io.platform.catalog.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanBundleRepository {

    PlanBundle save(PlanBundle planBundle);

    Optional<PlanBundle> findById(UUID id);

    Optional<PlanBundle> findByCode(String code);

    List<PlanBundle> findAllActive();

    List<PlanBundle> findAll();
}
