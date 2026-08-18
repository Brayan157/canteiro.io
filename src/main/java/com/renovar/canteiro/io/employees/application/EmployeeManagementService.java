package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeManagementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final EmployeeRepository employeeRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public EmployeeChangeResult create(CreateEmployeeCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(
                AccessModule.EMPLOYEES, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.EMPLOYEES, ChangeRequestOperation.CREATE, "Employee", null, 0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))), command.justification()
            ));
            return new EmployeeChangeResult(null, request, mode);
        }
        Employee employee = persist(companyId, command);
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.CREATE, "Employee", employee.getId(),
                null, auditData(employee), Map.of("origin", "direct"));
        return new EmployeeChangeResult(employee, null, mode);
    }

    @Transactional(readOnly = true)
    public Employee find(UUID employeeId) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.READ);
        return findInCurrentCompany(employeeId);
    }

    @Transactional(readOnly = true)
    public Page<Employee> findAll(Pageable pageable) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.READ);
        return employeeRepository.findByCompanyId(tenantContextHolder.requireCurrentTenant().companyId(), pageable);
    }

    @Transactional(readOnly = true)
    public Employee findInCurrentCompany(UUID employeeId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Employee"));
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest request) {
        Employee employee = persist(request.getCompanyId(), fromProposal(request.getSnapshot().proposedData().values()));
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.CREATE, "Employee", employee.getId(),
                null, auditData(employee), Map.of("origin", "approved-change-request", "changeRequestId", request.getId().toString()));
    }

    private Employee persist(UUID companyId, CreateEmployeeCommand command) {
        return employeeRepository.save(Employee.create(companyId, command.fullName(), command.jobTitle(), command.phone()));
    }

    private Map<String, Object> proposal(CreateEmployeeCommand command) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", command.fullName());
        values.put("jobTitle", command.jobTitle());
        values.put("phone", command.phone());
        return values;
    }

    private CreateEmployeeCommand fromProposal(Map<String, Object> values) {
        return new CreateEmployeeCommand((String) values.get("fullName"), (String) values.get("jobTitle"),
                (String) values.get("phone"), null);
    }

    private Map<String, Object> auditData(Employee employee) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", employee.getFullName());
        values.put("jobTitle", employee.getJobTitle());
        values.put("phone", employee.getPhone());
        values.put("active", employee.isActive());
        values.put("hasSystemAccess", employee.getUserId() != null);
        return values;
    }
}
