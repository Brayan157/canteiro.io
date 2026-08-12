package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;

import java.util.UUID;

public record CreateChangeRequestCommand(
        AuditModule module,
        ChangeRequestOperation operation,
        String entityType,
        UUID entityId,
        long entityVersion,
        ChangeRequestSnapshot snapshot,
        String justification
) {
}
