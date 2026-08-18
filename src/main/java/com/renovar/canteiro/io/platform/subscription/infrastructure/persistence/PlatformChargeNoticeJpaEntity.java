package com.renovar.canteiro.io.platform.subscription.infrastructure.persistence;

import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeStatus;
import com.renovar.canteiro.io.platform.subscription.domain.PlatformChargeNoticeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "platform_charge_notice")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformChargeNoticeJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "charge_id", nullable = false, updatable = false)
    private UUID chargeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 30, updatable = false)
    private PlatformChargeNoticeType noticeType;

    @Column(name = "recipient_email", nullable = false, length = 255, updatable = false)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PlatformChargeNoticeStatus status;

    @Column(name = "occurred_on", nullable = false, updatable = false)
    private LocalDate occurredOn;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlatformChargeNoticeJpaEntity(
            UUID id,
            UUID companyId,
            UUID chargeId,
            PlatformChargeNoticeType noticeType,
            String recipientEmail,
            PlatformChargeNoticeStatus status,
            LocalDate occurredOn,
            int deliveryAttempts,
            Instant lastAttemptAt,
            Instant deliveredAt,
            String failureReason
    ) {
        this.id = id;
        this.companyId = companyId;
        this.chargeId = chargeId;
        this.noticeType = noticeType;
        this.recipientEmail = recipientEmail;
        this.status = status;
        this.occurredOn = occurredOn;
        this.deliveryAttempts = deliveryAttempts;
        this.lastAttemptAt = lastAttemptAt;
        this.deliveredAt = deliveredAt;
        this.failureReason = failureReason;
    }

    public void beginDelivery(Instant attemptedAt) {
        status = PlatformChargeNoticeStatus.DELIVERING;
        deliveryAttempts++;
        lastAttemptAt = attemptedAt;
        failureReason = null;
    }

    public void applyDeliveryState(
            PlatformChargeNoticeStatus status,
            int deliveryAttempts,
            Instant lastAttemptAt,
            Instant deliveredAt,
            String failureReason
    ) {
        this.status = status;
        this.deliveryAttempts = deliveryAttempts;
        this.lastAttemptAt = lastAttemptAt;
        this.deliveredAt = deliveredAt;
        this.failureReason = failureReason;
    }
}
