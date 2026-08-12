package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignment;
import org.springframework.stereotype.Component;

@Component
public class PlanFeatureAssignmentPersistenceMapper {

    public PlanFeatureAssignmentJpaEntity toJpaEntity(PlanFeatureAssignment assignment) {
        return new PlanFeatureAssignmentJpaEntity(
                assignment.getPlanId(),
                assignment.getPlanFeatureId(),
                assignment.isActive()
        );
    }

    public void updateJpaEntity(PlanFeatureAssignmentJpaEntity entity, PlanFeatureAssignment assignment) {
        entity.update(assignment.isActive());
    }

    public PlanFeatureAssignment toDomain(PlanFeatureAssignmentJpaEntity entity) {
        return PlanFeatureAssignment.rehydrate(
                entity.getId(),
                entity.getPlanId(),
                entity.getPlanFeatureId(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
