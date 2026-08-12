package com.renovar.canteiro.io.platform.company.infrastructure.persistence;

import com.renovar.canteiro.io.platform.company.domain.CompanyOnboardingPlanSelection;
import com.renovar.canteiro.io.platform.company.domain.CompanyOnboardingPlanSelectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaCompanyOnboardingPlanSelectionRepository implements CompanyOnboardingPlanSelectionRepository {

    private final CompanyOnboardingPlanSelectionJpaRepository jpaRepository;

    @Override
    public CompanyOnboardingPlanSelection save(CompanyOnboardingPlanSelection selection) {
        if (selection.getId() != null) {
            throw new IllegalStateException("Onboarding plan selections are immutable");
        }
        return toDomain(jpaRepository.save(new CompanyOnboardingPlanSelectionJpaEntity(
                selection.getCompanyId(), selection.getPlanId(), selection.getSelectedAt()
        )));
    }

    @Override
    public List<CompanyOnboardingPlanSelection> findByCompanyId(UUID companyId) {
        return jpaRepository.findByCompanyIdOrderByPlanId(companyId).stream().map(this::toDomain).toList();
    }

    private CompanyOnboardingPlanSelection toDomain(CompanyOnboardingPlanSelectionJpaEntity entity) {
        return CompanyOnboardingPlanSelection.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getPlanId(), entity.getSelectedAt(), entity.getCreatedAt()
        );
    }
}
