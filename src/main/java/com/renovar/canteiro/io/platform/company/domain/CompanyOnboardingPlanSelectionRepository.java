package com.renovar.canteiro.io.platform.company.domain;

import java.util.List;
import java.util.UUID;

public interface CompanyOnboardingPlanSelectionRepository {

    CompanyOnboardingPlanSelection save(CompanyOnboardingPlanSelection selection);

    List<CompanyOnboardingPlanSelection> findByCompanyId(UUID companyId);
}
