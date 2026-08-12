package com.renovar.canteiro.io.platform.catalog.infrastructure.persistence;

import com.renovar.canteiro.io.platform.catalog.domain.PlanFeatureType;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "plan_feature")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanFeatureJpaEntity extends BaseJpaEntity {

    @Column(name = "code", nullable = false, length = 50, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, length = 20, updatable = false)
    private PlanFeatureType type;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    public PlanFeatureJpaEntity(String code, PlanFeatureType type, String name, String description, boolean active) {
        this.code = code;
        this.type = type;
        this.name = name;
        this.description = description;
        this.active = active;
    }

    public void update(String name, String description, boolean active) {
        this.name = name;
        this.description = description;
        this.active = active;
    }
}
