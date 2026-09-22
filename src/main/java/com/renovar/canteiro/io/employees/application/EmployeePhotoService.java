package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Stores an optional employee identification photo; it is never used for biometric processing. */
@Service
@RequiredArgsConstructor
public class EmployeePhotoService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final EmployeeRepository employeeRepository;
    private final StorageProvider storageProvider;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public String upload(UUID employeeId, byte[] content, String contentType) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.UPDATE_DIRECT);
        Employee employee = employee(employeeId);
        String storageKey = "companies/" + employee.getCompanyId() + "/employees/" + employee.getId()
                + "/photo-" + UUID.randomUUID() + extension(contentType);
        storageProvider.store(storageKey, content);
        String previousKey = employee.getPhotoStorageKey();
        try {
            employee.updatePhotoStorageKey(storageKey);
            employeeRepository.save(employee);
            auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.UPDATE, "Employee", employee.getId(),
                    Map.of("hasPhoto", previousKey != null), Map.of("hasPhoto", true),
                    Map.of("origin", "employee_identification_photo"));
            if (previousKey != null) storageProvider.delete(previousKey);
            return "/api/v1/company/employees/" + employee.getId() + "/photo";
        } catch (RuntimeException exception) {
            storageProvider.delete(storageKey);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public EmployeePhotoContent load(UUID employeeId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.READ);
        Employee employee = employee(employeeId);
        if (employee.getPhotoStorageKey() == null) throw new TenantResourceNotFoundException("Employee photo");
        return new EmployeePhotoContent(storageProvider.load(employee.getPhotoStorageKey()), contentType(employee.getPhotoStorageKey()));
    }

    @Transactional
    public void remove(UUID employeeId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.UPDATE_DIRECT);
        Employee employee = employee(employeeId);
        String storageKey = employee.getPhotoStorageKey();
        if (storageKey == null) throw new TenantResourceNotFoundException("Employee photo");
        employee.updatePhotoStorageKey(null);
        employeeRepository.save(employee);
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.UPDATE, "Employee", employee.getId(),
                Map.of("hasPhoto", true), Map.of("hasPhoto", false), Map.of("origin", "employee_identification_photo"));
        storageProvider.delete(storageKey);
    }

    private Employee employee(UUID employeeId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Employee"));
    }

    private String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            default -> throw new IllegalArgumentException("Unsupported employee photo media type");
        };
    }

    private String contentType(String storageKey) {
        if (storageKey.endsWith(".png")) return "image/png";
        if (storageKey.endsWith(".jpg")) return "image/jpeg";
        throw new IllegalStateException("Employee photo has an unsupported stored media type");
    }

    public record EmployeePhotoContent(byte[] content, String contentType) {
    }
}
