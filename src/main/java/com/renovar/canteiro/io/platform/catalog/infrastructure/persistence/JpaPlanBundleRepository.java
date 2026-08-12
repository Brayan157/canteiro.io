package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanBundle;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlanBundleRepository implements PlanBundleRepository {

    private final PlanBundleJpaRepository planBundleJpaRepository;
    private final PlanBundlePersistenceMapper planBundlePersistenceMapper;

    @Override
    public PlanBundle save(PlanBundle planBundle) {
        if (planBundle.getId() == null) {
            return planBundlePersistenceMapper.toDomain(
                    planBundleJpaRepository.save(planBundlePersistenceMapper.toJpaEntity(planBundle))
            );
        }
        PlanBundleJpaEntity entity = planBundleJpaRepository.findById(planBundle.getId())
                .orElseThrow(() -> new IllegalStateException("Plan bundle must exist before it can be updated"));
        planBundlePersistenceMapper.updateJpaEntity(entity, planBundle);
        return planBundlePersistenceMapper.toDomain(planBundleJpaRepository.save(entity));
    }

    @Override
    public Optional<PlanBundle> findById(UUID id) {
        return planBundleJpaRepository.findById(id).map(planBundlePersistenceMapper::toDomain);
    }

    @Override
    public Optional<PlanBundle> findByCode(String code) {
        return planBundleJpaRepository.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .map(planBundlePersistenceMapper::toDomain);
    }

    @Override
    public List<PlanBundle> findAllActive() {
        return planBundleJpaRepository.findByActiveTrue().stream().map(planBundlePersistenceMapper::toDomain).toList();
    }

    @Override
    public List<PlanBundle> findAll() {
        return planBundleJpaRepository.findAll().stream().map(planBundlePersistenceMapper::toDomain).toList();
    }
}
