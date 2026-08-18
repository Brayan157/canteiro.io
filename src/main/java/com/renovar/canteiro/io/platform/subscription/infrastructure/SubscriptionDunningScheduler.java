package com.renovar.canteiro.io.platform.subscription.infrastructure;

import com.renovar.canteiro.io.platform.subscription.application.SubscriptionDunningService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "subscription.dunning.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionDunningScheduler {

    private final SubscriptionDunningService subscriptionDunningService;

    @Scheduled(
            cron = "${subscription.dunning.schedule:0 15 3 * * *}",
            zone = "${subscription.dunning.zone:America/Sao_Paulo}"
    )
    public void evaluateDaily() {
        subscriptionDunningService.evaluateAll();
    }
}
