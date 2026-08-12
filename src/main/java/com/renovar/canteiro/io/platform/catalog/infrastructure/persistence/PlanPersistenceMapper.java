package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import org.springframework.stereotype.Component;

@Component
public class PlanPersistenceMapper {

    public PlanJpaEntity toJpaEntity(Plan plan) {
        return new PlanJpaEntity(plan.getCode(), plan.getName(), plan.getDescription(), plan.isActive());
    }

    public void updateJpaEntity(PlanJpaEntity entity, Plan plan) {
        entity.update(plan.getName(), plan.getDescription(), plan.isActive());
    }

    public Plan toDomain(PlanJpaEntity entity) {
        return Plan.rehydrate(
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
