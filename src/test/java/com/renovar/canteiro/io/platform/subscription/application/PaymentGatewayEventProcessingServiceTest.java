package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventStatus;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformCharge;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayEventProcessingServiceTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("ASAAS");
    private static final Instant NOW = Instant.parse("2026-08-17T16:00:00Z");

    @Mock private PaymentGatewayEventRepository eventRepository;
    @Mock private PlatformChargeRepository chargeRepository;
    @Mock private SubscriptionDunningCompanyService dunningCompanyService;
    @Mock private AuditEventRecorder auditEventRecorder;

    @Test
    void confirmsChargeAuditsAndReevaluatesCompanyAccess() {
        UUID eventId = UUID.randomUUID();
        PaymentGatewayEvent event = event(eventId);
        PlatformCharge charge = charge();
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(chargeRepository.findByProviderAndExternalChargeIdForUpdate(PROVIDER, "pay_1"))
                .thenReturn(Optional.of(charge));

        service().process(eventId);

        assertEquals(PaymentGatewayEventStatus.PROCESSED, event.getStatus());
        assertEquals(PlatformChargeStatus.CONFIRMED, charge.getStatus());
        verify(chargeRepository).save(charge);
        verify(eventRepository).save(event);
        verify(dunningCompanyService).reevaluateCompany(charge.getCompanyId());
        verify(auditEventRecorder, times(2)).recordSystemAction(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void marksEventAsFailedWhenChargeIsNotKnownAndAllowsLaterRetry() {
        UUID eventId = UUID.randomUUID();
        PaymentGatewayEvent event = event(eventId);
        when(eventRepository.findByIdForUpdate(eventId)).thenReturn(Optional.of(event));
        when(chargeRepository.findByProviderAndExternalChargeIdForUpdate(PROVIDER, "pay_1"))
                .thenReturn(Optional.empty());

        service().process(eventId);

        assertEquals(PaymentGatewayEventStatus.FAILED, event.getStatus());
        assertEquals("Platform charge was not found", event.getFailureReason());
        verify(eventRepository).save(event);
        verify(dunningCompanyService, never()).reevaluateCompany(any());
    }

    private PaymentGatewayEventProcessingService service() {
        return new PaymentGatewayEventProcessingService(
                eventRepository, chargeRepository, dunningCompanyService, auditEventRecorder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private PaymentGatewayEvent event(UUID id) {
        PaymentGatewayWebhook webhook = new PaymentGatewayWebhook(
                "evt_1", "pay_1", PaymentGatewayWebhookEventType.CHARGE_CONFIRMED,
                Instant.parse("2026-08-17T15:30:00Z"), Map.of("status", "RECEIVED")
        );
        return PaymentGatewayEvent.rehydrate(
                id, PROVIDER, webhook.externalEventId(), webhook.externalChargeId(), webhook.eventType(),
                webhook.occurredAt(), NOW.minusSeconds(60), webhook.attributes(),
                PaymentGatewayEventStatus.RECEIVED, null, null, NOW.minusSeconds(60), NOW.minusSeconds(60)
        );
    }

    private PlatformCharge charge() {
        return PlatformCharge.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), PROVIDER,
                "key", "cus_1", "pay_1", PaymentGatewayBillingMethod.PIX,
                new BigDecimal("99.90"), LocalDate.of(2026, 8, 17), PlatformChargeStatus.PENDING,
                NOW.minusSeconds(3600), NOW.minusSeconds(3600)
        );
    }
}
