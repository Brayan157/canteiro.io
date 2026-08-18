package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayEventServiceTest {

    private static final PaymentGatewayProviderCode PROVIDER = new PaymentGatewayProviderCode("TEST_GATEWAY");

    @Mock
    private PaymentGatewayEventRepository repository;

    @Test
    void storesAReceivedVerifiedWebhookOnlyOnce() {
        PaymentGatewayWebhook webhook = webhook();
        Instant receivedAt = Instant.parse("2026-08-12T20:00:00Z");
        when(repository.findByProviderAndExternalEventId(
                PROVIDER, "evt_123"
        )).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PaymentGatewayEventService service = new PaymentGatewayEventService(repository);

        PaymentGatewayEvent first = service.receiveVerifiedWebhook(
                PROVIDER, webhook, receivedAt
        );
        when(repository.findByProviderAndExternalEventId(
                PROVIDER, "evt_123"
        )).thenReturn(Optional.of(first));
        PaymentGatewayEvent duplicate = service.receiveVerifiedWebhook(
                PROVIDER, webhook, receivedAt.plusSeconds(5)
        );

        assertSame(first, duplicate);
        verify(repository, org.mockito.Mockito.times(2)).lockExternalEventId(
                PROVIDER, "evt_123"
        );
        verify(repository).save(any());
    }

    @Test
    void doesNotSaveAnAlreadyRecordedWebhook() {
        PaymentGatewayWebhook webhook = webhook();
        PaymentGatewayEvent existing = PaymentGatewayEvent.receive(
                PROVIDER, webhook, Instant.parse("2026-08-12T20:00:00Z")
        );
        when(repository.findByProviderAndExternalEventId(
                PROVIDER, "evt_123"
        )).thenReturn(Optional.of(existing));

        new PaymentGatewayEventService(repository).receiveVerifiedWebhook(
                PROVIDER, webhook, Instant.parse("2026-08-12T20:00:05Z")
        );

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAnExternalEventIdReusedWithDifferentContent() {
        PaymentGatewayWebhook original = webhook();
        PaymentGatewayEvent existing = PaymentGatewayEvent.receive(
                PROVIDER, original, Instant.parse("2026-08-12T20:00:00Z")
        );
        PaymentGatewayWebhook conflicting = new PaymentGatewayWebhook(
                "evt_123", "pay_other", PaymentGatewayWebhookEventType.CHARGE_CONFIRMED,
                original.occurredAt(), original.attributes()
        );
        when(repository.findByProviderAndExternalEventId(PROVIDER, "evt_123"))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () -> new PaymentGatewayEventService(repository)
                .receiveVerifiedWebhook(
                        PROVIDER, conflicting, Instant.parse("2026-08-12T20:00:05Z")
                ));

        verify(repository, never()).save(any());
    }

    private PaymentGatewayWebhook webhook() {
        return new PaymentGatewayWebhook(
                "evt_123", "pay_123", PaymentGatewayWebhookEventType.CHARGE_CONFIRMED,
                Instant.parse("2026-08-12T19:45:03Z"), Map.of("status", "RECEIVED")
        );
    }
}
