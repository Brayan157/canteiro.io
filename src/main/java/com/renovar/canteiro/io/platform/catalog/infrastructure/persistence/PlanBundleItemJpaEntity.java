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
@Table(name = "plan_bundle_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanBundleItemJpaEntity extends BaseJpaEntity {

    @Column(name = "plan_bundle_id", nullable = false, updatable = false)
    private UUID planBundleId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "active", nullable = false)
    private boolean active;

    public PlanBundleItemJpaEntity(UUID planBundleId, UUID planId, boolean active) {
        this.planBundleId = planBundleId;
        this.planId = planId;
        this.active = active;
    }

    public void update(boolean active) {
        this.active = active;
    }
}
