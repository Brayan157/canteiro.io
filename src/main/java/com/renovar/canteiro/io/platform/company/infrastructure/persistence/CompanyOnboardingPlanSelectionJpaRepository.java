package com.renovar.canteiro.io.platform.company.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CompanyOnboardingPlanSelectionJpaRepository extends JpaRepository<CompanyOnboardingPlanSelectionJpaEntity, UUID> {

    List<CompanyOnboardingPlanSelectionJpaEntity> findByCompanyIdOrderByPlanId(UUID companyId);
}
