package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.governance.domain.AuditEvent;
import com.renovar.canteiro.io.governance.domain.AuditPresentation;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuditPresentationService {
    private static final String HISTORICAL = "Registro histórico indisponível";
    private static final String UNKNOWN_ACTOR = "Usuário histórico indisponível";
    private static final Pattern UUID_TEXT = Pattern.compile("(?i)\\b[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\b");
    private final UserRepository userRepository;
    private final CompanyUserRepository companyUserRepository;
    private final EmployeeRepository employeeRepository;
    private final RoleRepository roleRepository;
    private final FinalCustomerRepository finalCustomerRepository;
    private final com.renovar.canteiro.io.payables.domain.SupplierRepository supplierRepository;
    private final com.renovar.canteiro.io.payables.domain.FinancialCategoryRepository categoryRepository;
    private final com.renovar.canteiro.io.payables.domain.PayableRepository payableRepository;

    public AuditPresentation capture(UUID companyId, UUID actorUserId, AuditActorType actorType, AuditAction action,
                                     String entityType, UUID entityId, Map<String, Object> metadata) {
        String actor = resolveActorDisplayName(companyId, actorUserId, actorType, null);
        String origin = origin(metadata, actorType);
        EntityLabel entity = entity(companyId, entityType, entityId, metadata);
        return new AuditPresentation(actor, entity.name(), entity.reference(), summary(actor, action, entity.name()), origin,
                "support".equals(origin) ? "Atuação de suporte autorizada" : null, correlationId(metadata));
    }

    public AuditPresentation resolveLegacy(AuditEvent event) {
        AuditPresentation stored = event.getPresentation();
        if (stored == null || stored.summary() == null) {
            return capture(event.getCompanyId(), event.getActorUserId(), event.getActorType(), event.getAction(),
                    event.getEntityType(), event.getEntityId(), event.getMetadata().values());
        }
        String actor = resolveActorDisplayName(event.getCompanyId(), event.getActorUserId(), event.getActorType(),
                stored.actorDisplayName());
        String name = stored.entityDisplayName();
        if (name == null || HISTORICAL.equals(name)) {
            name = recoverEntityName(event, name);
        }
        if (java.util.Objects.equals(actor, stored.actorDisplayName()) && java.util.Objects.equals(name, stored.entityDisplayName())) {
            return stored;
        }
        return new AuditPresentation(actor, name, stored.entityDisplayReference(),
                summary(actor, event.getAction(), name == null ? HISTORICAL : name), stored.origin(),
                stored.supportContext(), stored.correlationId());
    }

    private String recoverEntityName(AuditEvent event, String fallback) {
        String type = event.getEntityType();
        return switch (type) {
            case "FinalCustomer", "Role", "RolePermissions", "User", "UserRoles" -> {
                String current = entity(event.getCompanyId(), type, event.getEntityId(), event.getMetadata().values()).name();
                if (HISTORICAL.equals(current)) {
                    yield fallback;
                }
                if (("FinalCustomer".equals(type) || "Role".equals(type)) && event.getAfterData() != null
                        && event.getAfterData().values().get("name") instanceof String recorded) {
                    yield safeDisplayName(recorded, current);
                }
                yield current;
            }
            default -> fallback;
        };
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

    public String resolveActorDisplayName(UUID companyId, UUID userId, AuditActorType type, String storedName) {
        if (type == AuditActorType.SYSTEM) {
            return "Sistema";
        }
        String recorded = safeDisplayName(storedName, UNKNOWN_ACTOR);
        if (!UNKNOWN_ACTOR.equals(recorded)) {
            return recorded;
        }
        if (type != AuditActorType.COMPANY_USER || companyId == null || userId == null) {
            return UNKNOWN_ACTOR;
        }
        return companyUserDisplayName(companyId, userId).orElse(UNKNOWN_ACTOR);
    }

    private Optional<String> companyUserDisplayName(UUID companyId, UUID userId) {
        return companyUserRepository.findByUserIdAndCompanyId(userId, companyId).map(membership -> {
            String fallback = membership.isCompanyOwner() ? "Proprietário da empresa" : "Usuário da empresa";
            return employeeRepository.findFullNameByUserIdAndCompanyId(userId, companyId)
                    .map(name -> safeDisplayName(name, fallback)).orElse(fallback);
        });
    }

    private String safeDisplayName(String value, String fallback) {
        if (value == null || value.contains("@") || UUID_TEXT.matcher(value).find()) {
            return fallback;
        }
        String name = safe(value, fallback);
        return name.isBlank() ? fallback : name;
    }
    private EntityLabel entity(UUID companyId, String type, UUID id, Map<String, Object> metadata) {
        if (companyId != null && id != null) {
            if ("Role".equals(type) || "RolePermissions".equals(type)) {
                String name = roleRepository.findByIdAndCompanyId(id, companyId)
                        .map(role -> safeDisplayName(role.getName(), "Papel da empresa")).orElse(HISTORICAL);
                return new EntityLabel(name, null);
            }
            if ("User".equals(type) || "UserRoles".equals(type)) {
                return new EntityLabel(companyUserDisplayName(companyId, id).orElse(HISTORICAL), null);
            }
        }
        if ("FinalCustomer".equals(type) && companyId != null && id != null) {
            return finalCustomerRepository.findByIdAndCompanyId(id, companyId)
                    .map(customer -> new EntityLabel(safeDisplayName(customer.getName(), HISTORICAL), null))
                    .orElse(new EntityLabel(HISTORICAL, null));
        }
        if("InvoiceXmlPreview".equals(type))return new EntityLabel("Prévia de XML de nota fiscal",null);
        if ("InvoiceXmlFiscalSnapshot".equals(type)) return new EntityLabel("Evidência fiscal de nota fiscal", null);
        if ("PayablePaymentInstruction".equals(type) && id != null) return payableRepository.findByIdAndCompanyId(id,companyId)
                .map(p -> new EntityLabel("Instruções para pagamento",safe(p.getReference(),null))).orElse(new EntityLabel(HISTORICAL,null));
        if ("Payable".equals(type) && id != null) return payableRepository.findByIdAndCompanyId(id,companyId)
                .map(p -> new EntityLabel("Conta a pagar",safe(p.getReference(),null))).orElse(new EntityLabel(HISTORICAL,null));
        if ("Supplier".equals(type) && id != null) return supplierRepository.find(companyId,id)
                .map(s -> new EntityLabel(safe(s.name(),HISTORICAL),null)).orElse(new EntityLabel(HISTORICAL,null));
        if ("FinancialCategory".equals(type) && id != null) return categoryRepository.find(companyId,id)
                .map(c -> new EntityLabel(safe(c.name(),HISTORICAL),safe(c.code(),null))).orElse(new EntityLabel(HISTORICAL,null));
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
        if(action==AuditAction.VIEW_SENSITIVE&&"Prévia de XML de nota fiscal".equals(entity))return safe(actor+" consultou a prévia de um XML de nota fiscal.",HISTORICAL);
        return safe(actor + " " + switch (action) { case CREATE -> "criou"; case UPDATE -> "atualizou"; case DEACTIVATE -> "desativou"; case CANCEL -> "cancelou"; case APPROVE -> "aprovou"; case REJECT -> "rejeitou"; case VIEW_SENSITIVE -> "visualizou dados de pagamento de"; default -> "registrou uma ação em"; } + " " + entity + ".", HISTORICAL);
    }
    private String origin(Map<String,Object> metadata, AuditActorType type) { if (type == AuditActorType.SYSTEM) return "system"; Object value=metadata.get("origin"); return value instanceof String s && s.contains("approval") ? "approval" : type == AuditActorType.PLATFORM_USER ? "support" : "direct"; }
    private String correlationId(Map<String,Object> metadata) { Object value=metadata.get("correlationId"); return value instanceof String s ? safe(s, null) : null; }
    private UUID uuid(Object value) { try { return value instanceof String s ? UUID.fromString(s) : null; } catch (IllegalArgumentException ignored) { return null; } }
    private String safeType(String type) { return switch (type) { case "EmployeeAddress" -> "Endereço principal do funcionário"; case "EmployeePaymentAccount" -> "Referência de pagamento do funcionário"; case "Invoice" -> "Nota fiscal"; case "Receivable" -> "Conta a receber"; case "Measurement" -> "Medição"; default -> HISTORICAL; }; }
    private String safe(String value, String fallback) { if (value == null || value.isBlank()) return fallback; String clean=value.replaceAll("[\\p{Cntrl}]", " ").trim(); return clean.length() > 160 ? clean.substring(0,160) : clean; }
    private record EntityLabel(String name, String reference) { }
}
