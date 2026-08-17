package com.renovar.canteiro.io.platform.subscription.application;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventRepository;
import com.renovar.canteiro.io.platform.subscription.infrastructure.PaymentSynchronizationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class PaymentGatewayEventReprocessingService {

    private final PaymentGatewayEventRepository repository;
    private final PaymentGatewayEventProcessingService processingService;
    private final PaymentSynchronizationProperties properties;
    private final Clock clock;

    public int processRetryable() {
        var events = repository.findRetryable(clock.instant().minus(properties.retryAfter()), properties.batchSize());
        events.forEach(event -> processWithoutStarvingBatch(event.getId()));
        return events.size();
    }

    private void processWithoutStarvingBatch(java.util.UUID eventId) {
        try {
            processingService.process(eventId);
        } catch (RuntimeException exception) {
            processingService.markFailed(eventId, "Processing failed: " + exception.getClass().getSimpleName());
        }
    }
}
