package com.renovar.canteiro.io.governance.domain;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditPayloadTest {

    @Test
    void copiesPayloadValuesAndKeepsThemReadOnly() {
        Map<String, Object> originalValues = new LinkedHashMap<>();
        originalValues.put("status", "DRAFT");
        originalValues.put("discount", null);

        AuditPayload auditPayload = new AuditPayload(originalValues);
        originalValues.put("status", "APPROVED");

        assertEquals("DRAFT", auditPayload.values().get("status"));
        assertNull(auditPayload.values().get("discount"));
        assertThrows(UnsupportedOperationException.class, () -> auditPayload.values().put("new", "value"));
    }
}
