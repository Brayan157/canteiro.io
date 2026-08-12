package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanPrice;
import org.springframework.stereotype.Component;

@Component
public class PlanPricePersistenceMapper {

    public PlanPriceJpaEntity toJpaEntity(PlanPrice planPrice) {
        return new PlanPriceJpaEntity(
                planPrice.getPlanId(),
                planPrice.getAmount(),
                planPrice.getValidFrom(),
                planPrice.getValidUntil()
        );
    }

    public PlanPrice toDomain(PlanPriceJpaEntity entity) {
        return PlanPrice.rehydrate(
                entity.getId(),
                entity.getPlanId(),
                entity.getAmount(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public void updateJpaEntity(PlanPriceJpaEntity entity, PlanPrice planPrice) {
        entity.endOn(planPrice.getValidUntil());
    }
}
