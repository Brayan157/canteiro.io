package com.renovar.canteiro.io.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountActivationTokenTest {

    private static final Instant ISSUE_INSTANT = Instant.parse("2026-08-11T12:00:00Z");

    @Test
    void consumesAValidTokenOnlyOnce() {
        AccountActivationToken token = AccountActivationToken.create(
                UUID.randomUUID(),
                "token-hash",
                ISSUE_INSTANT.plusSeconds(3600)
        );

        token.consume(ISSUE_INSTANT);

        assertEquals(ISSUE_INSTANT, token.getConsumedAt());
        assertThrows(IllegalStateException.class, () -> token.consume(ISSUE_INSTANT.plusSeconds(1)));
    }

    @Test
    void rejectsAnExpiredToken() {
        AccountActivationToken token = AccountActivationToken.create(
                UUID.randomUUID(),
                "token-hash",
                ISSUE_INSTANT
        );

        assertThrows(IllegalStateException.class, () -> token.consume(ISSUE_INSTANT));
    }
}
