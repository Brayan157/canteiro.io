package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanBundlePrice;
import org.springframework.stereotype.Component;

@Component
public class PlanBundlePricePersistenceMapper {

    public PlanBundlePriceJpaEntity toJpaEntity(PlanBundlePrice planBundlePrice) {
        return new PlanBundlePriceJpaEntity(
                planBundlePrice.getPlanBundleId(),
                planBundlePrice.getAmount(),
                planBundlePrice.getValidFrom(),
                planBundlePrice.getValidUntil()
        );
    }

    public void updateJpaEntity(PlanBundlePriceJpaEntity entity, PlanBundlePrice planBundlePrice) {
        entity.endOn(planBundlePrice.getValidUntil());
    }

    public PlanBundlePrice toDomain(PlanBundlePriceJpaEntity entity) {
        return PlanBundlePrice.rehydrate(
                entity.getId(),
                entity.getPlanBundleId(),
                entity.getAmount(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
