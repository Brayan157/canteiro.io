package com.renovar.canteiro.io.platform.company.infrastructure.persistence;

import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "company_onboarding_plan_selection")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class CompanyOnboardingPlanSelectionJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "selected_at", nullable = false, updatable = false)
    private Instant selectedAt;

    CompanyOnboardingPlanSelectionJpaEntity(UUID companyId, UUID planId, Instant selectedAt) {
        this.companyId = companyId;
        this.planId = planId;
        this.selectedAt = selectedAt;
    }
}
