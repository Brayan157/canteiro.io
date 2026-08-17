package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventStatus;
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
public class PaymentGatewayEventProcessingService {

    private final PaymentGatewayEventRepository eventRepository;
    private final PlatformChargeRepository chargeRepository;
    private final SubscriptionDunningCompanyService dunningCompanyService;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID eventId) {
        PaymentGatewayEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Payment gateway event does not exist"));
        if (event.getStatus() == PaymentGatewayEventStatus.PROCESSED) {
            return;
        }
        PlatformCharge charge = chargeRepository.findByProviderAndExternalChargeIdForUpdate(
                event.getProvider(), event.getExternalChargeId()
        ).orElse(null);
        if (charge == null) {
            fail(event, "Platform charge was not found");
            return;
        }

        PaymentGatewayEventStatus previousEventStatus = event.getStatus();
        PlatformChargeStatus previousStatus = charge.getStatus();
        boolean changed = charge.applyGatewayStatus(toChargeStatus(event), event.getOccurredAt());
        chargeRepository.save(charge);
        event.markProcessed(clock.instant());
        eventRepository.save(event);
        auditEventRecorder.recordSystemAction(
                charge.getCompanyId(), AuditModule.PLATFORM, AuditAction.UPDATE,
                "PaymentGatewayEvent", event.getId(), Map.of("status", previousEventStatus.name()),
                Map.of("status", event.getStatus().name()), Map.of("origin", "payment-webhook")
        );
        if (changed) {
            auditEventRecorder.recordSystemAction(
                    charge.getCompanyId(), AuditModule.PLATFORM, AuditAction.UPDATE,
                    "PlatformCharge", charge.getId(), Map.of("status", previousStatus.name()),
                    Map.of("status", charge.getStatus().name()), Map.of("origin", "payment-webhook")
            );
            dunningCompanyService.reevaluateCompany(charge.getCompanyId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String reason) {
        PaymentGatewayEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Payment gateway event does not exist"));
        if (event.getStatus() != PaymentGatewayEventStatus.PROCESSED) {
            fail(event, reason);
        }
    }

    private void fail(PaymentGatewayEvent event, String reason) {
        PaymentGatewayEventStatus previousStatus = event.getStatus();
        event.markFailed(clock.instant(), reason);
        eventRepository.save(event);
        auditEventRecorder.recordSystemAction(
                null, AuditModule.PLATFORM, AuditAction.UPDATE, "PaymentGatewayEvent", event.getId(),
                Map.of("status", previousStatus.name()),
                Map.of("status", PaymentGatewayEventStatus.FAILED.name()),
                Map.of("origin", "payment-webhook", "reason", reason)
        );
    }

    private PlatformChargeStatus toChargeStatus(PaymentGatewayEvent event) {
        return switch (event.getEventType()) {
            case CHARGE_CREATED -> PlatformChargeStatus.PENDING;
            case CHARGE_CONFIRMED -> PlatformChargeStatus.CONFIRMED;
            case CHARGE_OVERDUE -> PlatformChargeStatus.OVERDUE;
            case CHARGE_CANCELLED -> PlatformChargeStatus.CANCELLED;
        };
    }
}
