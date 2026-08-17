package com.renovar.canteiro.io.platform.subscription.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustUnlockTest {

    @Test
    void remainsActiveOnlyFromItsStartUntilItsExpiration() {
        Instant startsAt = Instant.parse("2026-08-22T12:00:00Z");
        TrustUnlock trustUnlock = TrustUnlock.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Temporary payment agreement", startsAt,
                startsAt.plusSeconds(3600)
        );

        assertFalse(trustUnlock.isActiveAt(startsAt.minusSeconds(1)));
        assertTrue(trustUnlock.isActiveAt(startsAt));
        assertFalse(trustUnlock.isActiveAt(startsAt.plusSeconds(3600)));
    }

    @Test
    void rejectsAnExpirationThatDoesNotFollowTheStart() {
        Instant startsAt = Instant.parse("2026-08-22T12:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> TrustUnlock.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Invalid period", startsAt, startsAt
        ));
    }
}
