package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItem;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundleItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class JpaPlanBundleItemRepository implements PlanBundleItemRepository {

    private final PlanBundleItemJpaRepository planBundleItemJpaRepository;
    private final PlanBundleItemPersistenceMapper planBundleItemPersistenceMapper;

    @Override
    public PlanBundleItem save(PlanBundleItem item) {
        if (item.getId() == null) {
            return planBundleItemPersistenceMapper.toDomain(
                    planBundleItemJpaRepository.save(planBundleItemPersistenceMapper.toJpaEntity(item))
            );
        }
        PlanBundleItemJpaEntity entity = planBundleItemJpaRepository.findById(item.getId())
                .orElseThrow(() -> new IllegalStateException("Plan bundle item must exist before it can be updated"));
        planBundleItemPersistenceMapper.updateJpaEntity(entity, item);
        return planBundleItemPersistenceMapper.toDomain(planBundleItemJpaRepository.save(entity));
    }

    @Override
    public Set<UUID> findActivePlanIdsByPlanBundleId(UUID planBundleId) {
        return planBundleItemJpaRepository.findByPlanBundleIdAndActiveTrue(planBundleId).stream()
                .map(PlanBundleItemJpaEntity::getPlanId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
