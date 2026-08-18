package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEvent;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayProviderCode;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayEventPersistenceMapper {

    public PaymentGatewayEventJpaEntity toJpaEntity(PaymentGatewayEvent event) {
        return new PaymentGatewayEventJpaEntity(
                event.getProvider().value(), event.getExternalEventId(), event.getExternalChargeId(), event.getEventType(),
                event.getOccurredAt(), event.getReceivedAt(), event.getAttributes(), event.getStatus(),
                event.getProcessedAt(), event.getFailureReason()
        );
    }

    public PaymentGatewayEvent toDomain(PaymentGatewayEventJpaEntity entity) {
        return PaymentGatewayEvent.rehydrate(
                entity.getId(),
                new PaymentGatewayProviderCode(entity.getProvider()),
                entity.getExternalEventId(), entity.getExternalChargeId(),
                entity.getEventType(), entity.getOccurredAt(), entity.getReceivedAt(), entity.getAttributes(),
                entity.getStatus(), entity.getProcessedAt(), entity.getFailureReason(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
