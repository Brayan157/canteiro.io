package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeStatus;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "platform_charge")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformChargeJpaEntity extends BaseJpaEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "subscription_id", nullable = false, updatable = false)
    private UUID subscriptionId;
    @Column(name = "provider", nullable = false, length = 30, updatable = false)
    private String provider;
    @Column(name = "idempotency_key", nullable = false, length = 120, updatable = false)
    private String idempotencyKey;
    @Column(name = "external_customer_id", nullable = false, length = 100, updatable = false)
    private String externalCustomerId;
    @Column(name = "external_charge_id", nullable = false, length = 100, updatable = false)
    private String externalChargeId;
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_method", nullable = false, length = 30, updatable = false)
    private PaymentGatewayBillingMethod billingMethod;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;
    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlatformChargeStatus status;
    @Column(name = "last_gateway_event_at")
    private Instant lastGatewayEventAt;
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PlatformChargeJpaEntity(
            UUID companyId,
            UUID subscriptionId,
            String provider,
            String idempotencyKey,
            String externalCustomerId,
            String externalChargeId,
            PaymentGatewayBillingMethod billingMethod,
            BigDecimal amount,
            LocalDate dueDate,
            PlatformChargeStatus status
    ) {
        this.companyId = companyId;
        this.subscriptionId = subscriptionId;
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.externalCustomerId = externalCustomerId;
        this.externalChargeId = externalChargeId;
        this.billingMethod = billingMethod;
        this.amount = amount;
        this.dueDate = dueDate;
        this.status = status;
    }

    void updateLifecycle(PlatformChargeStatus newStatus, Instant gatewayEventAt) {
        this.status = newStatus;
        this.lastGatewayEventAt = gatewayEventAt;
    }
}
