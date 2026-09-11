package com.renovar.canteiro.io.governance.api;

import com.renovar.canteiro.io.governance.api.response.AuditEventResponse;
import com.renovar.canteiro.io.governance.application.AuditEventQueryService;
import com.renovar.canteiro.io.governance.application.AuditPresentationService;
import com.renovar.canteiro.io.governance.domain.AuditPresentation;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/company/audit-events")
@RequiredArgsConstructor
@Tag(name = "Audit events")
public class AuditEventController {

    private static final Set<String> SORT_FIELDS = Set.of("occurredAt", "createdAt");
    private static final List<String> DEFAULT_SORT = List.of("occurredAt,desc");
    private static final Set<String> SAFE_METADATA_KEYS = Set.of("origin");

    private final AuditEventQueryService auditEventQueryService;
    private final AuditPresentationService auditPresentationService;

    @GetMapping
    @Operation(summary = "Lists the safe audit history for the authenticated company",
            description = "Requires AUDIT.READ. actorDisplayName is a safe, human-readable name; actorEmail is a separate contact field when available. Before and after data are intentionally never returned.")
    public PageResponse<AuditEventResponse> findAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort
    ) {
        List<String> resolvedSort = sort == null || sort.isEmpty() ? DEFAULT_SORT : sort;
        return PageResponse.from(
                auditEventQueryService.findAll(new PageQuery(page, size, resolvedSort).toPageable(SORT_FIELDS)),
                this::toResponse
        );
    }

    private AuditEventResponse toResponse(AuditEvent auditEvent) {
        AuditPresentation presentation = auditPresentationService.resolveLegacy(auditEvent);
        return new AuditEventResponse(
                auditEvent.getId(),
                auditEvent.getOccurredAt(),
                auditEvent.getActorType(),
                auditEvent.getActorUserId(),
                presentation.actorDisplayName(),
                auditPresentationService.actorEmail(auditEvent),
                auditEvent.getModule(),
                auditEvent.getAction(),
                auditEvent.getEntityType(),
                auditEvent.getEntityId(),
                presentation.entityDisplayName(),
                presentation.entityDisplayReference(),
                presentation.summary(),
                presentation.origin(),
                presentation.supportContext(),
                presentation.correlationId(),
                safeMetadata(auditEvent)
        );
    }

    private Map<String, Object> safeMetadata(AuditEvent auditEvent) {
        Map<String, Object> source = auditEvent.getMetadata().values();
        Map<String, Object> safeMetadata = new LinkedHashMap<>();
        SAFE_METADATA_KEYS.forEach(key -> {
            Object value = source.get(key);
            if (value instanceof String text && text.length() <= 100) {
                safeMetadata.put(key, text);
            }
        });
        return Map.copyOf(safeMetadata);
    }
}
