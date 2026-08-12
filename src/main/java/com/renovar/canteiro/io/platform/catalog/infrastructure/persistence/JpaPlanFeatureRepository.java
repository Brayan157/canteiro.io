package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanFeature;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlanFeatureRepository implements PlanFeatureRepository {

    private final PlanFeatureJpaRepository planFeatureJpaRepository;
    private final PlanFeaturePersistenceMapper planFeaturePersistenceMapper;

    @Override
    public PlanFeature save(PlanFeature planFeature) {
        if (planFeature.getId() == null) {
            return planFeaturePersistenceMapper.toDomain(
                    planFeatureJpaRepository.save(planFeaturePersistenceMapper.toJpaEntity(planFeature))
            );
        }
        PlanFeatureJpaEntity entity = planFeatureJpaRepository.findById(planFeature.getId())
                .orElseThrow(() -> new IllegalStateException("Plan feature must exist before it can be updated"));
        planFeaturePersistenceMapper.updateJpaEntity(entity, planFeature);
        return planFeaturePersistenceMapper.toDomain(planFeatureJpaRepository.save(entity));
    }

    @Override
    public Optional<PlanFeature> findById(UUID id) {
        return planFeatureJpaRepository.findById(id).map(planFeaturePersistenceMapper::toDomain);
    }

    @Override
    public Optional<PlanFeature> findByCode(String code) {
        return planFeatureJpaRepository.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .map(planFeaturePersistenceMapper::toDomain);
    }

    @Override
    public List<PlanFeature> findAll() {
        return planFeatureJpaRepository.findAll().stream().map(planFeaturePersistenceMapper::toDomain).toList();
    }
}
