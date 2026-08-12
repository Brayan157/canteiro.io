package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.CatalogPricingSource;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItem;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItemRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionSnapshotServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionItemRepository subscriptionItemRepository;
    @Mock
    private PlanRepository planRepository;

    @Test
    void persistsTheQuotedPriceAndThePlanIdentityAndNamesAtTheTimeOfSubscription() {
        UUID companyId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID financialPlanId = UUID.randomUUID();
        UUID reportingPlanId = UUID.randomUUID();
        LocalDate priceDate = LocalDate.of(2026, 8, 12);
        CatalogPriceQuote quote = new CatalogPriceQuote(
                Set.of(financialPlanId, reportingPlanId), new BigDecimal("149.90"), priceDate,
                CatalogPricingSource.INDIVIDUAL_PLANS, null
        );
        Subscription persistedSubscription = Subscription.rehydrate(
                subscriptionId, companyId, SubscriptionStatus.PENDING_ACTIVATION, new BigDecimal("149.90"),
                CatalogPricingSource.INDIVIDUAL_PLANS, null, priceDate, null, null, null, null
        );
        when(subscriptionRepository.save(any())).thenReturn(persistedSubscription);
        when(planRepository.findById(financialPlanId)).thenReturn(Optional.of(plan(financialPlanId, "FINANCIAL", "Financeiro")));
        when(planRepository.findById(reportingPlanId)).thenReturn(Optional.of(plan(reportingPlanId, "REPORTING", "Relatórios")));
        SubscriptionSnapshotService service = new SubscriptionSnapshotService(
                subscriptionRepository, subscriptionItemRepository, planRepository
        );

        Subscription subscription = service.createInitialSubscription(companyId, quote);

        assertEquals(subscriptionId, subscription.getId());
        ArgumentCaptor<SubscriptionItem> items = ArgumentCaptor.forClass(SubscriptionItem.class);
        verify(subscriptionItemRepository, org.mockito.Mockito.times(2)).save(items.capture());
        List<SubscriptionItem> capturedItems = items.getAllValues();
        assertEquals(Set.of("FINANCIAL", "REPORTING"), capturedItems.stream()
                .map(SubscriptionItem::getPlanCode)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("Financeiro", "Relatórios"), capturedItems.stream()
                .map(SubscriptionItem::getPlanName)
                .collect(java.util.stream.Collectors.toSet()));
        capturedItems.forEach(item -> assertEquals(subscriptionId, item.getSubscriptionId()));
    }

    private Plan plan(UUID id, String code, String name) {
        return Plan.rehydrate(id, code, name, null, true, null, null);
    }
}
