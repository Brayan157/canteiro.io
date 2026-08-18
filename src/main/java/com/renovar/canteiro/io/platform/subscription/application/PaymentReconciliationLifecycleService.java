package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PaymentReconciliationLifecycleService {

    private final PlatformChargeRepository chargeRepository;
    private final SubscriptionDunningCompanyService dunningCompanyService;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(UUID chargeId, PaymentGatewayChargeStatus remoteStatus) {
        PlatformCharge charge = chargeRepository.findByIdForUpdate(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("Platform charge does not exist"));
        PlatformChargeStatus before = charge.getStatus();
        boolean changed = charge.applyGatewayStatus(PlatformChargeStatus.valueOf(remoteStatus.name()), clock.instant());
        chargeRepository.save(charge);
        if (changed) {
            auditEventRecorder.recordSystemAction(
                    charge.getCompanyId(), AuditModule.PLATFORM, AuditAction.UPDATE, "PlatformCharge", charge.getId(),
                    Map.of("status", before.name()), Map.of("status", charge.getStatus().name()),
                    Map.of("origin", "payment-reconciliation")
            );
            dunningCompanyService.reevaluateCompany(charge.getCompanyId());
        }
        return changed;
    }
}
