package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanBundle;
import org.springframework.stereotype.Component;

@Component
public class PlanBundlePersistenceMapper {

    public PlanBundleJpaEntity toJpaEntity(PlanBundle planBundle) {
        return new PlanBundleJpaEntity(
                planBundle.getCode(),
                planBundle.getName(),
                planBundle.getDescription(),
                planBundle.isActive()
        );
    }

    public void updateJpaEntity(PlanBundleJpaEntity entity, PlanBundle planBundle) {
        entity.update(planBundle.getName(), planBundle.getDescription(), planBundle.isActive());
    }

    public PlanBundle toDomain(PlanBundleJpaEntity entity) {
        return PlanBundle.rehydrate(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
