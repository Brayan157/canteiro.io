package com.renovar.canteiro.io.platform.subscription.domain;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubscriptionTest {

    @Test
    void createsPendingSubscriptionWithTheQuotedBundleSnapshot() {
        UUID companyId = UUID.randomUUID();
        UUID bundleId = UUID.randomUUID();
        CatalogPriceQuote quote = new CatalogPriceQuote(
                Set.of(UUID.randomUUID(), UUID.randomUUID()), new BigDecimal("179.90"),
                LocalDate.of(2026, 8, 12), CatalogPricingSource.PLAN_BUNDLE, bundleId
        );

        Subscription subscription = Subscription.create(companyId, quote);

        assertEquals(companyId, subscription.getCompanyId());
        assertEquals(SubscriptionStatus.PENDING_ACTIVATION, subscription.getStatus());
        assertEquals(new BigDecimal("179.90"), subscription.getQuotedAmount());
        assertEquals(CatalogPricingSource.PLAN_BUNDLE, subscription.getPricingSource());
        assertEquals(bundleId, subscription.getPlanBundleId());
        assertEquals(LocalDate.of(2026, 8, 12), subscription.getPricingEffectiveDate());
    }

    @Test
    void rejectsARehydratedIndividualPlanSnapshotWithABundle() {
        assertThrows(IllegalArgumentException.class, () -> Subscription.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), SubscriptionStatus.PENDING_ACTIVATION,
                new BigDecimal("99.90"), CatalogPricingSource.INDIVIDUAL_PLANS, UUID.randomUUID(),
                LocalDate.of(2026, 8, 12), null, null, null, null
        ));
    }

    @Test
    void startsTrialForThirtyDaysAndMovesToAwaitingPaymentOnItsEndDate() {
        Subscription subscription = Subscription.create(UUID.randomUUID(), individualPlanQuote());
        LocalDate startedOn = LocalDate.of(2026, 8, 12);

        assertEquals(true, subscription.startTrial(startedOn));
        assertEquals(SubscriptionStatus.TRIAL, subscription.getStatus());
        assertEquals(startedOn, subscription.getTrialStartedOn());
        assertEquals(LocalDate.of(2026, 9, 11), subscription.getTrialEndsOn());
        assertEquals(false, subscription.advanceTrial(LocalDate.of(2026, 9, 10)));
        assertEquals(true, subscription.advanceTrial(LocalDate.of(2026, 9, 11)));
        assertEquals(SubscriptionStatus.AWAITING_PAYMENT, subscription.getStatus());
    }

    private CatalogPriceQuote individualPlanQuote() {
        return new CatalogPriceQuote(
                Set.of(UUID.randomUUID()), new BigDecimal("99.90"), LocalDate.of(2026, 8, 12),
                CatalogPricingSource.INDIVIDUAL_PLANS, null
        );
    }
}
