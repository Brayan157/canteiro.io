package com.renovar.canteiro.io.governance.api.response;

import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ChangeRequestResponse(
        UUID id,
        UUID requesterUserId,
        ChangeRequestRequesterResponse requester,
        AuditModule module,
        ChangeRequestOperation operation,
        String entityType,
        UUID entityId,
        ChangeRequestEntityResponse entity,
        long entityVersion,
        int revision,
        ChangeRequestStatus status,
        Map<String, Object> previousData,
        Map<String, Object> proposedData,
        Map<String, Object> proposedSummary,
        String justification,
        Instant createdAt
) {
}
