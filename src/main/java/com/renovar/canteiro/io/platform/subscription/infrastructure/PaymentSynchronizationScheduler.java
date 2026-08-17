package com.renovar.canteiro.io.platform.subscription.infrastructure;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGateway;
import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayEventReprocessingService;
import com.renovar.canteiro.io.platform.subscription.application.PaymentReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnBean(PaymentGateway.class)
@ConditionalOnProperty(value = "subscription.payment-synchronization.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentSynchronizationScheduler {

    private final PaymentGatewayEventReprocessingService reprocessingService;
    private final PaymentReconciliationService reconciliationService;
    private final PaymentSynchronizationProperties properties;

    @Scheduled(fixedDelayString = "${subscription.payment-synchronization.event-delay:PT10S}")
    public void processEvents() {
        reprocessingService.processRetryable();
    }

    @Scheduled(fixedDelayString = "${subscription.payment-synchronization.reconciliation-delay:PT30M}")
    public void reconcileCharges() {
        reconciliationService.reconcile(properties.batchSize());
    }
}
