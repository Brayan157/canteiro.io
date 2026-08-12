package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlanBundlePriceRepository implements PlanBundlePriceRepository {

    private final PlanBundlePriceJpaRepository planBundlePriceJpaRepository;
    private final PlanBundlePricePersistenceMapper planBundlePricePersistenceMapper;

    @Override
    public PlanBundlePrice save(PlanBundlePrice planBundlePrice) {
        if (planBundlePrice.getId() == null) {
            return planBundlePricePersistenceMapper.toDomain(
                    planBundlePriceJpaRepository.save(planBundlePricePersistenceMapper.toJpaEntity(planBundlePrice))
            );
        }
        PlanBundlePriceJpaEntity entity = planBundlePriceJpaRepository.findById(planBundlePrice.getId())
                .orElseThrow(() -> new IllegalStateException("Plan bundle price must exist before it can be updated"));
        planBundlePricePersistenceMapper.updateJpaEntity(entity, planBundlePrice);
        return planBundlePricePersistenceMapper.toDomain(planBundlePriceJpaRepository.save(entity));
    }

    @Override
    public List<PlanBundlePrice> findByPlanBundleId(UUID planBundleId) {
        return planBundlePriceJpaRepository.findByPlanBundleIdOrderByValidFromAsc(planBundleId).stream()
                .map(planBundlePricePersistenceMapper::toDomain)
                .toList();
    }
}
