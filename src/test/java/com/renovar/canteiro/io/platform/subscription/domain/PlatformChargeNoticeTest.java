package com.renovar.canteiro.io.platform.subscription.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformChargeNoticeTest {

    @Test
    void recordsDeliveryAttemptsAndTransitionsToDelivered() {
        PlatformChargeNotice notice = notice();
        Instant attemptedAt = Instant.parse("2026-08-22T12:00:00Z");

        notice.beginDelivery(attemptedAt);
        notice.markDelivered(attemptedAt.plusSeconds(5));

        assertEquals(PlatformChargeNoticeStatus.DELIVERED, notice.getStatus());
        assertEquals(1, notice.getDeliveryAttempts());
        assertEquals(attemptedAt, notice.getLastAttemptAt());
        assertEquals(attemptedAt.plusSeconds(5), notice.getDeliveredAt());
    }

    @Test
    void permitsRetryAfterDeliveryFailureButNotAfterDelivery() {
        PlatformChargeNotice notice = notice();
        Instant now = Instant.parse("2026-08-22T12:00:00Z");
        notice.beginDelivery(now);
        notice.markDeliveryFailed("SmtpException");
        notice.beginDelivery(now.plusSeconds(60));

        assertEquals(PlatformChargeNoticeStatus.DELIVERING, notice.getStatus());
        assertEquals(2, notice.getDeliveryAttempts());
        notice.markDelivered(now.plusSeconds(61));

        assertThrows(IllegalStateException.class, () -> notice.beginDelivery(now.plusSeconds(120)));
    }

    private PlatformChargeNotice notice() {
        return PlatformChargeNotice.create(
                UUID.randomUUID(), UUID.randomUUID(), PlatformChargeNoticeType.DUE_DATE,
                "billing@example.com", LocalDate.of(2026, 8, 22)
        );
    }
}
