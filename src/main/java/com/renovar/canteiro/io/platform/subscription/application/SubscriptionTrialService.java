package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionTrialService {

    private final CompanyUserRepository companyUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    @Transactional
    public void startTrialForCompanyUser(UUID userId) {
        companyUserRepository.findByUserId(userId).ifPresent(companyUser -> subscriptionRepository
                .findByCompanyId(companyUser.getCompanyId()).stream()
                .filter(subscription -> subscription.getStatus() == SubscriptionStatus.PENDING_ACTIVATION)
                .findFirst()
                .ifPresent(subscription -> {
                    subscription.startTrial(LocalDate.now(clock));
                    subscriptionRepository.save(subscription);
                }));
    }

    @Transactional
    public int advanceExpiredTrials() {
        LocalDate currentDate = LocalDate.now(clock);
        return subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL).stream()
                .filter(subscription -> subscription.advanceTrial(currentDate))
                .map(subscriptionRepository::save)
                .toList()
                .size();
    }
}
