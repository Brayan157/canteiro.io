package com.renovar.canteiro.io.platform.subscription.domain;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class PlatformChargeNotice {

    private final UUID id;
    private final UUID companyId;
    private final UUID chargeId;
    private final PlatformChargeNoticeType noticeType;
    private final String recipientEmail;
    private PlatformChargeNoticeStatus status;
    private final LocalDate occurredOn;
    private int deliveryAttempts;
    private Instant lastAttemptAt;
    private Instant deliveredAt;
    private String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private PlatformChargeNotice(
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
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = require(id, "Platform charge notice id is required");
        this.companyId = require(companyId, "Platform charge notice company is required");
        this.chargeId = require(chargeId, "Platform charge notice charge is required");
        this.noticeType = require(noticeType, "Platform charge notice type is required");
        this.recipientEmail = requireEmail(recipientEmail);
        this.status = require(status, "Platform charge notice status is required");
        this.occurredOn = require(occurredOn, "Platform charge notice occurrence date is required");
        if (deliveryAttempts < 0) {
            throw new IllegalArgumentException("Platform charge notice delivery attempts cannot be negative");
        }
        this.deliveryAttempts = deliveryAttempts;
        this.lastAttemptAt = lastAttemptAt;
        this.deliveredAt = deliveredAt;
        this.failureReason = normalizeFailureReason(failureReason);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlatformChargeNotice create(
            UUID companyId,
            UUID chargeId,
            PlatformChargeNoticeType noticeType,
            String recipientEmail,
            LocalDate occurredOn
    ) {
        return new PlatformChargeNotice(
                UUID.randomUUID(), companyId, chargeId, noticeType, recipientEmail,
                PlatformChargeNoticeStatus.PENDING_DELIVERY, occurredOn, 0, null, null, null, null, null
        );
    }

    public static PlatformChargeNotice rehydrate(
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
            String failureReason,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PlatformChargeNotice(
                id, companyId, chargeId, noticeType, recipientEmail, status, occurredOn,
                deliveryAttempts, lastAttemptAt, deliveredAt, failureReason, createdAt, updatedAt
        );
    }

    public void beginDelivery(Instant attemptedAt) {
        if (status != PlatformChargeNoticeStatus.PENDING_DELIVERY
                && status != PlatformChargeNoticeStatus.DELIVERY_FAILED
                && status != PlatformChargeNoticeStatus.DELIVERING) {
            throw new IllegalStateException("Platform charge notice cannot be delivered in its current status");
        }
        status = PlatformChargeNoticeStatus.DELIVERING;
        deliveryAttempts++;
        lastAttemptAt = require(attemptedAt, "Platform charge notice delivery attempt time is required");
        failureReason = null;
    }

    public void markDelivered(Instant deliveredAt) {
        requireDelivering();
        status = PlatformChargeNoticeStatus.DELIVERED;
        this.deliveredAt = require(deliveredAt, "Platform charge notice delivery time is required");
        failureReason = null;
    }

    public void markDeliveryFailed(String reason) {
        requireDelivering();
        status = PlatformChargeNoticeStatus.DELIVERY_FAILED;
        failureReason = normalizeFailureReason(reason);
    }

    public void cancel() {
        requireDelivering();
        status = PlatformChargeNoticeStatus.CANCELLED;
        failureReason = null;
    }

    private void requireDelivering() {
        if (status != PlatformChargeNoticeStatus.DELIVERING) {
            throw new IllegalStateException("Platform charge notice is not being delivered");
        }
    }

    private static String normalizeFailureReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private static String requireEmail(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("Platform charge notice recipient email is required");
        }
        return value.trim();
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
