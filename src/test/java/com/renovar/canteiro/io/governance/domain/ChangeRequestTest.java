package com.renovar.canteiro.io.governance.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeRequestTest {

    @Test
    void startsPendingWithFirstRevisionAndInitialVersion() {
        ChangeRequest changeRequest = ChangeRequest.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AuditModule.CONTRACTS,
                ChangeRequestOperation.CREATE,
                "Contract",
                null,
                0,
                new ChangeRequestSnapshot(null, new AuditPayload(Map.of("name", "New contract"))),
                null
        );

        assertEquals(ChangeRequestStatus.PENDING, changeRequest.getStatus());
        assertEquals(1, changeRequest.getRevision());
        assertEquals(0, changeRequest.getVersion());
        assertEquals(0, changeRequest.getEntityVersion());
    }

    @Test
    void requiresAnEntityForUpdateAndCancelRequests() {
        ChangeRequestSnapshot snapshot = new ChangeRequestSnapshot(
                new AuditPayload(Map.of("status", "DRAFT")),
                new AuditPayload(Map.of("status", "ACTIVE"))
        );

        assertThrows(IllegalArgumentException.class, () -> ChangeRequest.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AuditModule.CONTRACTS,
                ChangeRequestOperation.UPDATE,
                "Contract",
                null,
                3,
                snapshot,
                "Requires manager approval"
        ));
    }
}
