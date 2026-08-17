package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayWebhookEventType;
import com.renovar.canteiro.io.platform.subscription.domain.PaymentGatewayEventStatus;
import com.renovar.canteiro.io.shared.infrastructure.persistence.jpa.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Getter
@Entity
@Table(name = "payment_gateway_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentGatewayEventJpaEntity extends BaseJpaEntity {

    @Column(name = "provider", nullable = false, length = 30, updatable = false)
    private String provider;
    @Column(name = "external_event_id", nullable = false, length = 150, updatable = false)
    private String externalEventId;
    @Column(name = "external_charge_id", nullable = false, length = 100, updatable = false)
    private String externalChargeId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40, updatable = false)
    private PaymentGatewayWebhookEventType eventType;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, String> attributes;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentGatewayEventStatus status;
    @Column(name = "processed_at")
    private Instant processedAt;
    @Column(name = "failure_reason", length = 1000)
    private String failureReason;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PaymentGatewayEventJpaEntity(
            String provider,
            String externalEventId,
            String externalChargeId,
            PaymentGatewayWebhookEventType eventType,
            Instant occurredAt,
            Instant receivedAt,
            Map<String, String> attributes,
            PaymentGatewayEventStatus status,
            Instant processedAt,
            String failureReason
    ) {
        this.provider = provider;
        this.externalEventId = externalEventId;
        this.externalChargeId = externalChargeId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.attributes = attributes;
        this.status = status;
        this.processedAt = processedAt;
        this.failureReason = failureReason;
    }

    void updateProcessing(PaymentGatewayEventStatus newStatus, Instant newProcessedAt, String newFailureReason) {
        this.status = newStatus;
        this.processedAt = newProcessedAt;
        this.failureReason = newFailureReason;
    }
}
