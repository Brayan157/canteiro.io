package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignment;
import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaPlanFeatureAssignmentRepository implements PlanFeatureAssignmentRepository {

    private final PlanFeatureAssignmentJpaRepository planFeatureAssignmentJpaRepository;
    private final PlanFeatureAssignmentPersistenceMapper planFeatureAssignmentPersistenceMapper;

    @Override
    public PlanFeatureAssignment save(PlanFeatureAssignment assignment) {
        if (assignment.getId() == null) {
            return planFeatureAssignmentPersistenceMapper.toDomain(
                    planFeatureAssignmentJpaRepository.save(planFeatureAssignmentPersistenceMapper.toJpaEntity(assignment))
            );
        }
        PlanFeatureAssignmentJpaEntity entity = planFeatureAssignmentJpaRepository.findById(assignment.getId())
                .orElseThrow(() -> new IllegalStateException("Plan feature assignment must exist before it can be updated"));
        planFeatureAssignmentPersistenceMapper.updateJpaEntity(entity, assignment);
        return planFeatureAssignmentPersistenceMapper.toDomain(planFeatureAssignmentJpaRepository.save(entity));
    }

    @Override
    public List<PlanFeatureAssignment> findActiveByPlanId(UUID planId) {
        return planFeatureAssignmentJpaRepository.findByPlanIdAndActiveTrue(planId).stream()
                .map(planFeatureAssignmentPersistenceMapper::toDomain)
                .toList();
    }
}
