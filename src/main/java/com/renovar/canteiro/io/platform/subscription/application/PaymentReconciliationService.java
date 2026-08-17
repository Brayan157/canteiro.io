package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "integrations.asaas", name = "enabled", havingValue = "true")
public class PaymentReconciliationService {

    private final PaymentGateway paymentGateway;
    private final PlatformChargeRepository chargeRepository;
    private final PaymentReconciliationLifecycleService lifecycleService;

    public int reconcile(int limit) {
        int changed = 0;
        for (PlatformCharge candidate : chargeRepository.findReconciliationCandidates(limit)) {
            try {
                PaymentGatewayChargeStatus remoteStatus = paymentGateway.findChargeStatus(
                        candidate.getExternalChargeId()
                );
                if (lifecycleService.apply(candidate.getId(), remoteStatus)) {
                    changed++;
                }
            } catch (PaymentGatewayException exception) {
                log.warn("Payment reconciliation failed for platform charge {}", candidate.getId(), exception);
            }
        }
        return changed;
    }

}
