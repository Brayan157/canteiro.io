package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditEventRepository;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.platform.support.application.PlatformOperatorContextHolder;
import com.renovar.canteiro.io.platform.support.application.SupportTargetContextHolder;
import com.renovar.canteiro.io.shared.infrastructure.web.CorrelationIdFilter;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditEventRecorder {

    private final AuditEventRepository auditEventRepository;
    private final TenantContextHolder tenantContextHolder;
    private final PlatformOperatorContextHolder platformOperatorContextHolder;
    private final SupportTargetContextHolder supportTargetContextHolder;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent recordDirectAction(
            AuditModule module,
            AuditAction action,
            String entityType,
            UUID entityId,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Map<String, Object> metadata
    ) {
        AuditActor actor = resolveActor();
        return auditEventRepository.append(AuditEvent.create(
                actor.companyId(),
                actor.userId(),
                actor.actorType(),
                module,
                action,
                entityType,
                entityId,
                toPayload(beforeData),
                toPayload(afterData),
                toMetadataPayload(metadata),
                clock.instant()
        ));
    }

    private AuditActor resolveActor() {
        return tenantContextHolder.currentTenant()
                .map(tenant -> new AuditActor(tenant.userId(), tenant.companyId(), AuditActorType.COMPANY_USER))
                .or(() -> platformOperatorContextHolder.currentOperator().map(operator -> new AuditActor(
                        operator.userId(),
                        supportTargetContextHolder.currentTarget()
                                .map(target -> target.targetCompanyId())
                                .orElse(null),
                        AuditActorType.PLATFORM_USER
                )))
                .orElseThrow(() -> new IllegalStateException("A trusted actor context is required to record an audit event"));
    }

    private AuditPayload toPayload(Map<String, Object> data) {
        return data == null ? null : new AuditPayload(data);
    }

    private AuditPayload toMetadataPayload(Map<String, Object> metadata) {
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }
        mergedMetadata.put("correlationId", correlationId());
        return new AuditPayload(mergedMetadata);
    }

    private String correlationId() {
        String correlationId = MDC.get(CorrelationIdFilter.HEADER_NAME);
        return correlationId == null ? "internal" : correlationId;
    }

    private record AuditActor(UUID userId, UUID companyId, AuditActorType actorType) {
    }
}
