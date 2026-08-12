package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import com.renovar.canteiro.io.platform.catalog.domain.PlanPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlanPriceRepository implements PlanPriceRepository {

    private final PlanPriceJpaRepository planPriceJpaRepository;
    private final PlanPricePersistenceMapper planPricePersistenceMapper;

    @Override
    public PlanPrice save(PlanPrice planPrice) {
        if (planPrice.getId() == null) {
            return planPricePersistenceMapper.toDomain(
                    planPriceJpaRepository.save(planPricePersistenceMapper.toJpaEntity(planPrice))
            );
        }
        PlanPriceJpaEntity entity = planPriceJpaRepository.findById(planPrice.getId())
                .orElseThrow(() -> new IllegalStateException("Plan price must exist before it can be updated"));
        planPricePersistenceMapper.updateJpaEntity(entity, planPrice);
        return planPricePersistenceMapper.toDomain(planPriceJpaRepository.save(entity));
    }

    @Override
    public List<PlanPrice> findByPlanId(UUID planId) {
        return planPriceJpaRepository.findByPlanIdOrderByValidFromAsc(planId).stream()
                .map(planPricePersistenceMapper::toDomain)
                .toList();
    }
}
