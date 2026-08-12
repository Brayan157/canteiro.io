package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanFeature;
import org.springframework.stereotype.Component;

@Component
public class PlanFeaturePersistenceMapper {

    public PlanFeatureJpaEntity toJpaEntity(PlanFeature planFeature) {
        return new PlanFeatureJpaEntity(
                planFeature.getCode(),
                planFeature.getType(),
                planFeature.getName(),
                planFeature.getDescription(),
                planFeature.isActive()
        );
    }

    public void updateJpaEntity(PlanFeatureJpaEntity entity, PlanFeature planFeature) {
        entity.update(planFeature.getName(), planFeature.getDescription(), planFeature.isActive());
    }

    public PlanFeature toDomain(PlanFeatureJpaEntity entity) {
        return PlanFeature.rehydrate(
                entity.getId(),
                entity.getCode(),
                entity.getType(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
