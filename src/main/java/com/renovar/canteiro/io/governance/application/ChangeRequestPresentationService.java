package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Builds the safe, human-readable view used by the company approval inbox. */
@Service
@RequiredArgsConstructor
public class ChangeRequestPresentationService {

    private static final String HISTORICAL = "Registro histórico indisponível";

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public ChangeRequestListItem present(ChangeRequest request) {
        Map<String, Object> previous = safePayload(request, previousPayload(request));
        Map<String, Object> proposed = safePayload(request, request.getSnapshot().proposedData().values());
        Map<String, Object> proposedSummary = employeeSummary(request.getModule(), request.getSnapshot().proposedData().values());
        return new ChangeRequestListItem(
                request,
                requester(request),
                entity(request, proposedSummary),
                previous,
                proposed,
                proposedSummary
        );
    }

    public boolean matchesSearch(ChangeRequestListItem item, String search) {
        if (search == null) {
            return true;
        }
        String normalizedSearch = search.toLowerCase(Locale.ROOT);
        return contains(item.requester().displayName(), normalizedSearch)
                || contains(item.entity().displayName(), normalizedSearch);
    }

    private ChangeRequestRequesterProjection requester(ChangeRequest request) {
        return employeeRepository.findByUserIdAndCompanyId(request.getRequesterUserId(), request.getCompanyId())
                .map(employee -> new ChangeRequestRequesterProjection(
                        request.getRequesterUserId(),
                        safeText(employee.getFullName(), HISTORICAL),
                        employee.getId(),
                        employee.getPhotoStorageKey() == null ? null
                                : "/api/v1/company/employees/" + employee.getId() + "/photo"
                ))
                .orElseGet(() -> new ChangeRequestRequesterProjection(
                        request.getRequesterUserId(),
                        userRepository.findById(request.getRequesterUserId())
                                .map(user -> safeText(user.getEmail(), "Usuário da empresa"))
                                .orElse(HISTORICAL),
                        null,
                        null
                ));
    }

    private ChangeRequestEntityProjection entity(ChangeRequest request, Map<String, Object> proposedSummary) {
        String displayName = switch (request.getModule()) {
            case EMPLOYEES -> employeeDisplayName(request, proposedSummary);
            default -> entityTitle(request.getSnapshot().proposedData().values(), previousPayload(request));
        };
        return new ChangeRequestEntityProjection(request.getEntityType(), request.getEntityId(), displayName);
    }

    private String employeeDisplayName(ChangeRequest request, Map<String, Object> proposedSummary) {
        if (request.getEntityId() != null) {
            return employeeRepository.findByIdAndCompanyId(request.getEntityId(), request.getCompanyId())
                    .map(Employee::getFullName)
                    .map(value -> safeText(value, HISTORICAL))
                    .orElseGet(() -> stringValue(proposedSummary.get("fullName"), HISTORICAL));
        }
        return stringValue(proposedSummary.get("fullName"), HISTORICAL);
    }

    private Map<String, Object> employeeSummary(AuditModule module, Map<String, Object> payload) {
        if (module != AuditModule.EMPLOYEES) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fullName", nullableText(payload.get("fullName")));
        summary.put("jobTitle", nullableText(payload.get("jobTitle")));
        summary.put("department", nullableText(payload.get("department")));
        summary.put("employmentType", enumValue(payload.get("employmentType")));
        summary.put("currentlyEmployed", booleanValue(payload.get("currentlyEmployed")));
        summary.put("hasDocument", hasValue(payload.get("documentNumber")) || Boolean.TRUE.equals(payload.get("hasDocument")));
        summary.put("hasCompensationAmount", hasValue(payload.get("compensationAmount"))
                || Boolean.TRUE.equals(payload.get("hasCompensationAmount")));
        return Collections.unmodifiableMap(summary);
    }

    private Map<String, Object> safePayload(ChangeRequest request, Map<String, Object> payload) {
        if (request.getModule() == AuditModule.EMPLOYEES) {
            return employeeSummary(request.getModule(), payload);
        }
        Map<String, Object> safePayload = new LinkedHashMap<>();
        copySafeText(payload, safePayload, "name");
        copySafeText(payload, safePayload, "title");
        copySafeText(payload, safePayload, "code");
        copySafeText(payload, safePayload, "reference");
        copySafeText(payload, safePayload, "status");
        copySafeBoolean(payload, safePayload, "active");
        return Collections.unmodifiableMap(safePayload);
    }

    private String entityTitle(Map<String, Object> proposed, Map<String, Object> previous) {
        for (String key : new String[]{"name", "title", "reference", "code"}) {
            String value = nullableText(proposed.get(key));
            if (value != null) {
                return value;
            }
            value = nullableText(previous.get(key));
            if (value != null) {
                return value;
            }
        }
        return HISTORICAL;
    }

    private Map<String, Object> previousPayload(ChangeRequest request) {
        return request.getSnapshot().beforeData() == null ? Map.of() : request.getSnapshot().beforeData().values();
    }

    private void copySafeText(Map<String, Object> source, Map<String, Object> target, String key) {
        String value = nullableText(source.get(key));
        if (value != null) {
            target.put(key, value);
        }
    }

    private void copySafeBoolean(Map<String, Object> source, Map<String, Object> target, String key) {
        Boolean value = booleanValue(source.get(key));
        if (value != null) {
            target.put(key, value);
        }
    }

    private boolean contains(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

    private String enumValue(Object value) {
        return value instanceof String text && text.matches("[A-Z_]{1,64}") ? text : null;
    }

    private Boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    private boolean hasValue(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private String stringValue(Object value, String fallback) {
        String text = nullableText(value);
        return text == null ? fallback : text;
    }

    private String nullableText(Object value) {
        return value instanceof String text && !text.isBlank() ? safeText(text, null) : null;
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (cleaned.isEmpty()) {
            return fallback;
        }
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }
}
