package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlanRepository implements PlanRepository {

    private final PlanJpaRepository planJpaRepository;
    private final PlanPersistenceMapper planPersistenceMapper;

    @Override
    public Plan save(Plan plan) {
        if (plan.getId() == null) {
            return planPersistenceMapper.toDomain(planJpaRepository.save(planPersistenceMapper.toJpaEntity(plan)));
        }
        PlanJpaEntity entity = planJpaRepository.findById(plan.getId())
                .orElseThrow(() -> new IllegalStateException("Plan must exist before it can be updated"));
        planPersistenceMapper.updateJpaEntity(entity, plan);
        return planPersistenceMapper.toDomain(planJpaRepository.save(entity));
    }

    @Override
    public Optional<Plan> findById(UUID id) {
        return planJpaRepository.findById(id).map(planPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Plan> findByCode(String code) {
        return planJpaRepository.findByCode(code.trim().toUpperCase(Locale.ROOT)).map(planPersistenceMapper::toDomain);
    }

    @Override
    public List<Plan> findAll() {
        return planJpaRepository.findAll().stream().map(planPersistenceMapper::toDomain).toList();
    }
}
