package com.renovar.canteiro.io.governance.api.response;

import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Safe audit projection for company users. Historical before/after payloads are deliberately not
 * part of this contract because they may contain protected operational or personal information.
 */
@Schema(description = "Tenant-scoped audit event without before/after payloads")
public record AuditEventResponse(
        UUID id,
        @Schema(description = "When the audited action occurred, always represented in UTC") Instant occurredAt,
        AuditActorType actorType,
        @Schema(description = "Trusted actor identifier; null only for SYSTEM events") UUID actorUserId,
        @Schema(description = "Safe, human-readable name for the audit timeline. Never contains an email address, UUID, or other technical identifier.", example = "João Silva")
        String actorDisplayName,
        @Schema(description = "Actor contact email when available. This is separate from actorDisplayName and is null for SYSTEM or unavailable historical actors.", example = "joao.silva@empresa.com")
        String actorEmail,
        AuditModule module,
        AuditAction action,
        String entityType,
        UUID entityId,
        String entityDisplayName,
        String entityDisplayReference,
        String summary,
        String origin,
        String supportContext,
        String correlationId,
        @Schema(description = "Only explicitly allow-listed, non-sensitive audit metadata") Map<String, Object> metadata
) {
}
