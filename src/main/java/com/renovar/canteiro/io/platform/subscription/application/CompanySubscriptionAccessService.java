package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionAccessLevel;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionDunningPolicy;
import com.renovar.canteiro.io.platform.subscription.domain.TrustUnlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanySubscriptionAccessService {

    private final PlatformChargeRepository platformChargeRepository;
    private final TrustUnlockRepository trustUnlockRepository;
    private final SubscriptionDunningPolicy subscriptionDunningPolicy;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SubscriptionAccessLevel resolveAccessLevel(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company subscription access requires a company");
        }
        LocalDate currentDate = LocalDate.now(clock);
        Set<UUID> trustedChargeIds = trustUnlockRepository.findActiveChargeIdsByCompanyId(companyId, clock.instant());
        return subscriptionDunningPolicy.assess(
                platformChargeRepository.findOutstandingByCompanyId(companyId), currentDate, trustedChargeIds
        ).accessLevel();
    }
}
