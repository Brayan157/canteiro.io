package com.renovar.canteiro.io.governance.domain;

public record ChangeRequestSnapshot(AuditPayload beforeData, AuditPayload proposedData) {

    public ChangeRequestSnapshot {
        if (proposedData == null) {
            throw new IllegalArgumentException("Proposed data is required");
        }
    }
}
