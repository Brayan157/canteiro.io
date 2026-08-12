package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "plan_feature_assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanFeatureAssignmentJpaEntity extends BaseJpaEntity {

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "plan_feature_id", nullable = false, updatable = false)
    private UUID planFeatureId;

    @Column(name = "active", nullable = false)
    private boolean active;

    public PlanFeatureAssignmentJpaEntity(UUID planId, UUID planFeatureId, boolean active) {
        this.planId = planId;
        this.planFeatureId = planFeatureId;
        this.active = active;
    }

    public void update(boolean active) {
        this.active = active;
    }
}
