package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.infrastructure.PaymentSynchronizationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayEventReprocessingServiceTest {

    @Mock private PaymentGatewayEventRepository repository;
    @Mock private PaymentGatewayEventProcessingService processingService;

    @Test
    void recordsFailureAndContinuesWithTheRemainingBatch() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PaymentGatewayEvent first = event(firstId);
        PaymentGatewayEvent second = event(secondId);
        Instant now = Instant.parse("2026-08-17T16:00:00Z");
        when(repository.findRetryable(now.minus(Duration.ofMinutes(5)), 50))
                .thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("temporary")).when(processingService).process(firstId);

        int processed = new PaymentGatewayEventReprocessingService(
                repository, processingService,
                new PaymentSynchronizationProperties(50, Duration.ofMinutes(5)),
                Clock.fixed(now, ZoneOffset.UTC)
        ).processRetryable();

        assertEquals(2, processed);
        verify(processingService).markFailed(firstId, "Processing failed: IllegalStateException");
        verify(processingService).process(secondId);
    }

    private PaymentGatewayEvent event(UUID id) {
        PaymentGatewayEvent event = mock(PaymentGatewayEvent.class);
        when(event.getId()).thenReturn(id);
        return event;
    }
}
