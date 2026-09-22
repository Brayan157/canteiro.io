package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.governance.domain.*;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditPresentationService {
    private static final String HISTORICAL = "Registro histórico indisponível";
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public AuditPresentation capture(UUID companyId, UUID actorUserId, AuditActorType actorType, AuditAction action,
                                     String entityType, UUID entityId, Map<String, Object> metadata) {
        String actor = actor(companyId, actorUserId, actorType);
        String origin = origin(metadata, actorType);
        EntityLabel entity = entity(companyId, entityType, entityId, metadata);
        return new AuditPresentation(actor, entity.name(), entity.reference(), summary(actor, action, entity.name()), origin,
                "support".equals(origin) ? "Atuação de suporte autorizada" : null, correlationId(metadata));
    }

    public AuditPresentation resolveLegacy(AuditEvent event) {
        AuditPresentation stored = event.getPresentation();
        if (stored != null && stored.summary() != null && !looksLikeEmail(stored.actorDisplayName())) return stored;
        return capture(event.getCompanyId(), event.getActorUserId(), event.getActorType(), event.getAction(),
                event.getEntityType(), event.getEntityId(), event.getMetadata().values());
    }

    /**
     * Email is a contact attribute, never the human-readable identity shown in the audit timeline.
     * It remains available as a separate field to callers already authorized to read the audit log.
     */
    public String actorEmail(AuditEvent event) {
        if (event.getActorType() == AuditActorType.SYSTEM || event.getActorUserId() == null) return null;
        return userRepository.findById(event.getActorUserId())
                .map(user -> safe(user.getEmail(), null))
                .orElse(null);
    }

    private String actor(UUID companyId, UUID userId, AuditActorType type) {
        if (type == AuditActorType.SYSTEM) return "Sistema";
        if (userId == null || userRepository.findById(userId).isEmpty()) return "Usuário histórico indisponível";
        return employeeRepository.findByUserIdAndCompanyId(userId, companyId)
                .map(employee -> safe(employee.getFullName(), "Usuário histórico indisponível"))
                .orElse("Usuário histórico indisponível");
    }
    private EntityLabel entity(UUID companyId, String type, UUID id, Map<String, Object> metadata) {
        UUID employeeId = "Employee".equals(type) ? id : uuid(metadata.get("employeeId"));
        if (employeeId != null) {
            return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                    .map(employee -> new EntityLabel("EmployeeAddress".equals(type) ? "Endereço principal do funcionário"
                                    : "EmployeePaymentAccount".equals(type) ? "Referência de pagamento do funcionário" : safe(employee.getFullName(), HISTORICAL),
                            employee.getRegistrationNumber() == null ? null : "Matrícula " + safe(employee.getRegistrationNumber(), "")))
                    .orElse(new EntityLabel(HISTORICAL, null));
        }
        return new EntityLabel(safeType(type), null);
    }
    private String summary(String actor, AuditAction action, String entity) {
        return safe(actor + " " + switch (action) { case CREATE -> "criou"; case UPDATE -> "atualizou"; case DEACTIVATE -> "desativou"; case CANCEL -> "cancelou"; case APPROVE -> "aprovou"; case REJECT -> "rejeitou"; case VIEW_SENSITIVE -> "visualizou dados de pagamento de"; default -> "registrou uma ação em"; } + " " + entity + ".", HISTORICAL);
    }
    private String origin(Map<String,Object> metadata, AuditActorType type) { if (type == AuditActorType.SYSTEM) return "system"; Object value=metadata.get("origin"); return value instanceof String s && s.contains("approval") ? "approval" : type == AuditActorType.PLATFORM_USER ? "support" : "direct"; }
    private String correlationId(Map<String,Object> metadata) { Object value=metadata.get("correlationId"); return value instanceof String s ? safe(s, null) : null; }
    private UUID uuid(Object value) { try { return value instanceof String s ? UUID.fromString(s) : null; } catch (IllegalArgumentException ignored) { return null; } }
    private String safeType(String type) { return switch (type) { case "EmployeeAddress" -> "Endereço principal do funcionário"; case "EmployeePaymentAccount" -> "Referência de pagamento do funcionário"; case "Invoice" -> "Nota fiscal"; case "Receivable" -> "Conta a receber"; case "Measurement" -> "Medição"; default -> HISTORICAL; }; }
    private String safe(String value, String fallback) { if (value == null || value.isBlank()) return fallback; String clean=value.replaceAll("[\\p{Cntrl}]", " ").trim(); return clean.length() > 160 ? clean.substring(0,160) : clean; }
    private boolean looksLikeEmail(String value) { return value != null && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"); }
    private record EntityLabel(String name, String reference) { }
}
