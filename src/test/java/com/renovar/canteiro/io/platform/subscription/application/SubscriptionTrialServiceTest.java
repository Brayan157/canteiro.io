package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionTrialServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private CompanyUserRepository companyUserRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Test
    void startsThePendingCompanySubscriptionTrialWhenTheCompanyUserActivates() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Subscription pendingSubscription = Subscription.create(companyId, quote());
        when(companyUserRepository.findByUserId(userId)).thenReturn(Optional.of(
                CompanyUser.rehydrate(UUID.randomUUID(), userId, companyId, null, null)
        ));
        when(subscriptionRepository.findByCompanyId(companyId)).thenReturn(List.of(pendingSubscription));
        SubscriptionTrialService service = new SubscriptionTrialService(
                companyUserRepository, subscriptionRepository, CLOCK
        );

        service.startTrialForCompanyUser(userId);

        ArgumentCaptor<Subscription> savedSubscription = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(savedSubscription.capture());
        assertEquals(SubscriptionStatus.TRIAL, savedSubscription.getValue().getStatus());
        assertEquals(LocalDate.of(2026, 8, 12), savedSubscription.getValue().getTrialStartedOn());
        assertEquals(LocalDate.of(2026, 9, 11), savedSubscription.getValue().getTrialEndsOn());
    }

    @Test
    void advancesOnlyExpiredTrials() {
        UUID companyId = UUID.randomUUID();
        Subscription expiredTrial = Subscription.create(companyId, quote());
        expiredTrial.startTrial(LocalDate.of(2026, 7, 13));
        Subscription currentTrial = Subscription.create(UUID.randomUUID(), quote());
        currentTrial.startTrial(LocalDate.of(2026, 8, 1));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.TRIAL)).thenReturn(List.of(expiredTrial, currentTrial));
        SubscriptionTrialService service = new SubscriptionTrialService(
                companyUserRepository, subscriptionRepository, CLOCK
        );

        int transitionedSubscriptions = service.advanceExpiredTrials();

        assertEquals(1, transitionedSubscriptions);
        assertEquals(SubscriptionStatus.AWAITING_PAYMENT, expiredTrial.getStatus());
        assertEquals(SubscriptionStatus.TRIAL, currentTrial.getStatus());
        verify(subscriptionRepository).save(expiredTrial);
    }

    private CatalogPriceQuote quote() {
        return new CatalogPriceQuote(
                Set.of(UUID.randomUUID()), new BigDecimal("99.90"), LocalDate.of(2026, 8, 12),
                CatalogPricingSource.INDIVIDUAL_PLANS, null
        );
    }
}
