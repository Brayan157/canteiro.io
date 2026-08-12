package com.renovar.canteiro.io.governance.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record AuditPayload(Map<String, Object> values) {

    public AuditPayload {
        values = values == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static AuditPayload empty() {
        return new AuditPayload(Map.of());
    }
}
