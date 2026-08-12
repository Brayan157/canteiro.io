package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.catalog.domain.CatalogPriceQuote;
import com.renovar.canteiro.io.platform.catalog.domain.Plan;
import com.renovar.canteiro.io.platform.catalog.domain.PlanRepository;
import com.renovar.canteiro.io.platform.subscription.domain.Subscription;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItem;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionItemRepository;
import com.renovar.canteiro.io.platform.subscription.domain.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionSnapshotService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionItemRepository subscriptionItemRepository;
    private final PlanRepository planRepository;

    @Transactional
    public Subscription createInitialSubscription(UUID companyId, CatalogPriceQuote quote) {
        Subscription subscription = subscriptionRepository.save(Subscription.create(companyId, quote));
        quote.planIds().stream()
                .sorted()
                .map(this::requirePlan)
                .map(plan -> SubscriptionItem.create(
                        subscription.getId(), plan.getId(), plan.getCode(), plan.getName()
                ))
                .forEach(subscriptionItemRepository::save);
        return subscription;
    }

    private Plan requirePlan(UUID planId) {
        return planRepository.findById(planId)
                .orElseThrow(() -> new IllegalStateException("Quoted plan must exist before a subscription is created"));
    }
}
