package com.renovar.canteiro.io.platform.subscription.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanySubscriptionAccessRepository {

    void lockCompanyId(UUID companyId);

    CompanySubscriptionAccess save(CompanySubscriptionAccess companySubscriptionAccess);

    Optional<CompanySubscriptionAccess> findByCompanyId(UUID companyId);

    List<CompanySubscriptionAccess> findAll();
}
