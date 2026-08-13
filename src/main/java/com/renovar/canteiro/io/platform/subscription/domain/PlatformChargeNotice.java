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
    private final PlatformChargeNoticeStatus status;
    private final LocalDate occurredOn;
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
                PlatformChargeNoticeStatus.PENDING_DELIVERY, occurredOn, null, null
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
            Instant createdAt,
            Instant updatedAt
    ) {
        return new PlatformChargeNotice(
                id, companyId, chargeId, noticeType, recipientEmail, status, occurredOn, createdAt, updatedAt
        );
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
