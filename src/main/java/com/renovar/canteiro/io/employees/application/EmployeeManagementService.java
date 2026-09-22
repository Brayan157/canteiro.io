package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.employees.domain.Employee;
import com.renovar.canteiro.io.employees.domain.EmployeeRepository;
import com.renovar.canteiro.io.employees.domain.EmployeeRegistrationNumberGenerator;
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
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeManagementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService authorizationService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeRegistrationNumberGenerator employeeRegistrationNumberGenerator;
    private final CompanyRepository companyRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public EmployeeChangeResult create(CreateEmployeeCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        ChangeAuthorizationMode mode = authorizationService.requireChangeAuthorization(
                AccessModule.EMPLOYEES, ChangeOperation.CREATE
        );
        Employee candidate = newEmployee(companyId, command);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.EMPLOYEES, ChangeRequestOperation.CREATE, "Employee", null, 0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))), command.justification()
            ));
            return new EmployeeChangeResult(null, request, mode);
        }
        Employee employee = employeeRepository.save(candidate);
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
    public Page<Employee> findAll(EmployeeFilter filter, Pageable pageable) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.READ);
        return employeeRepository.findByCompanyId(tenantContextHolder.requireCurrentTenant().companyId(), filter, pageable);
    }

    @Transactional(readOnly = true)
    public Employee findInCurrentCompany(UUID employeeId) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        return employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Employee"));
    }

    @Transactional
    public Employee applyApprovedCreation(ChangeRequest request) {
        Employee employee = persist(request.getCompanyId(), fromProposal(request.getSnapshot().proposedData().values()));
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.CREATE, "Employee", employee.getId(),
                null, auditData(employee), Map.of("origin", "approved-change-request", "changeRequestId", request.getId().toString()));
        return employee;
    }

    private Employee persist(UUID companyId, CreateEmployeeCommand command) {
        return employeeRepository.save(newEmployee(companyId, command));
    }

    @Transactional
    public Employee update(UUID employeeId, CreateEmployeeCommand command) {
        authorizationService.requirePermission(AccessModule.EMPLOYEES, AccessAction.UPDATE_DIRECT);
        Employee current = findInCurrentCompany(employeeId);
        Employee updated = current.updateProfile(command.fullName(), command.preferredName(), command.jobTitle(), command.department(),
                command.phone(), command.documentType(), command.documentNumber(), command.pis(), command.birthDate(),
                command.employmentType(), command.employmentStartDate(), command.employmentEndDate(), command.currentlyEmployed(),
                command.gender(), command.ethnicity(), command.compensationFrequency(), decimal(command.compensationAmount()));
        Employee saved = employeeRepository.save(updated);
        auditEventRecorder.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.UPDATE, "Employee", saved.getId(),
                auditData(current), auditData(saved), Map.of("origin", "direct"));
        return saved;
    }

    private Employee newEmployee(UUID companyId, CreateEmployeeCommand command) {
        String registrationNumber = command.registrationNumber() == null || command.registrationNumber().isBlank()
                ? employeeRegistrationNumberGenerator.nextRegistrationNumber(companyId,
                companyRepository.findById(companyId).orElseThrow().getCorporateName())
                : command.registrationNumber();
        return Employee.create(companyId, command.fullName(), command.preferredName(), command.jobTitle(),
                command.department(), command.phone(), registrationNumber, command.documentType(),
                command.documentNumber(), command.pis(), command.birthDate(), command.employmentType(), command.employmentStartDate(),
                command.employmentEndDate(), command.currentlyEmployed(), command.gender(), command.ethnicity(),
                command.compensationFrequency(), decimal(command.compensationAmount()));
    }

    private Map<String, Object> proposal(CreateEmployeeCommand command) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", command.fullName());
        values.put("preferredName", command.preferredName());
        values.put("jobTitle", command.jobTitle());
        values.put("department", command.department());
        values.put("phone", command.phone());
        values.put("registrationNumber", command.registrationNumber());
        values.put("documentType", command.documentType() == null ? null : command.documentType().name());
        values.put("documentNumber", command.documentNumber());
        values.put("pis", command.pis());
        values.put("birthDate", command.birthDate() == null ? null : command.birthDate().toString());
        values.put("employmentType", command.employmentType() == null ? null : command.employmentType().name());
        values.put("employmentStartDate", command.employmentStartDate() == null ? null : command.employmentStartDate().toString());
        values.put("employmentEndDate", command.employmentEndDate() == null ? null : command.employmentEndDate().toString());
        values.put("currentlyEmployed", command.currentlyEmployed());
        values.put("gender", command.gender() == null ? null : command.gender().name());
        values.put("ethnicity", command.ethnicity() == null ? null : command.ethnicity().name());
        values.put("compensationAmount", command.compensationAmount());
        values.put("compensationFrequency", command.compensationFrequency() == null ? null : command.compensationFrequency().name());
        return values;
    }

    private CreateEmployeeCommand fromProposal(Map<String, Object> values) {
        return new CreateEmployeeCommand(
                (String) values.get("fullName"),
                (String) values.get("preferredName"),
                (String) values.get("jobTitle"),
                (String) values.get("department"),
                (String) values.get("phone"),
                (String) values.get("registrationNumber"),
                enumValue(com.renovar.canteiro.io.employees.domain.EmployeeDocumentType.class, values.get("documentType")),
                (String) values.get("documentNumber"),
                (String) values.get("pis"),
                localDate(values.get("birthDate")),
                enumValue(com.renovar.canteiro.io.employees.domain.EmployeeEmploymentType.class, values.get("employmentType")),
                localDate(values.get("employmentStartDate")),
                localDate(values.get("employmentEndDate")),
                (Boolean) values.get("currentlyEmployed"),
                enumValue(com.renovar.canteiro.io.employees.domain.EmployeeGender.class, values.get("gender")),
                enumValue(com.renovar.canteiro.io.employees.domain.EmployeeEthnicity.class, values.get("ethnicity")),
                (String) values.get("compensationAmount"),
                enumValue(com.renovar.canteiro.io.employees.domain.EmployeeCompensationFrequency.class, values.get("compensationFrequency")),
                null
        );
    }

    private Map<String, Object> auditData(Employee employee) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fullName", employee.getFullName());
        values.put("jobTitle", employee.getJobTitle());
        values.put("department", employee.getDepartment());
        values.put("employmentType", employee.getEmploymentType() == null ? null : employee.getEmploymentType().name());
        values.put("documentType", employee.getDocumentType() == null ? null : employee.getDocumentType().name());
        values.put("hasDocument", employee.getDocumentNumber() != null);
        values.put("hasPis", employee.getPis() != null);
        values.put("currentlyEmployed", employee.isCurrentlyEmployed());
        values.put("gender", employee.getGender() == null ? null : employee.getGender().name());
        values.put("ethnicity", employee.getEthnicity() == null ? null : employee.getEthnicity().name());
        values.put("compensationFrequency", employee.getCompensationFrequency() == null ? null : employee.getCompensationFrequency().name());
        values.put("hasCompensationAmount", employee.getCompensationAmount() != null);
        values.put("active", employee.isActive());
        values.put("hasSystemAccess", employee.getUserId() != null);
        return values;
    }

    private BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    private java.time.LocalDate localDate(Object value) {
        return value == null ? null : java.time.LocalDate.parse((String) value);
    }

    private <T extends Enum<T>> T enumValue(Class<T> type, Object value) {
        return value == null ? null : Enum.valueOf(type, (String) value);
    }
}
