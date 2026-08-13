package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccess;
import com.renovar.canteiro.io.platform.subscription.domain.CompanySubscriptionAccessRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionDunningService {

    private final PlatformChargeRepository platformChargeRepository;
    private final CompanySubscriptionAccessRepository companySubscriptionAccessRepository;
    private final SubscriptionDunningCompanyService subscriptionDunningCompanyService;

    public SubscriptionDunningRunResult evaluateAll() {
        LinkedHashSet<UUID> companyIds = new LinkedHashSet<>(
                platformChargeRepository.findCompanyIdsWithOutstandingCharges()
        );
        companySubscriptionAccessRepository.findAll().stream()
                .map(CompanySubscriptionAccess::getCompanyId)
                .forEach(companyIds::add);

        return companyIds.stream()
                .sorted(Comparator.comparing(UUID::toString))
                .map(subscriptionDunningCompanyService::evaluateCompany)
                .reduce(SubscriptionDunningRunResult.empty(), SubscriptionDunningRunResult::plus);
    }

    public SubscriptionDunningRunResult evaluateCompany(UUID companyId) {
        return subscriptionDunningCompanyService.evaluateCompany(companyId);
    }
}
