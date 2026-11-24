package com.renovar.canteiro.io.workforce.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.api.error.FieldViolation;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import com.renovar.canteiro.io.workforce.domain.EmployeeEmployment;
import com.renovar.canteiro.io.workforce.domain.WorkAccessGrant;
import com.renovar.canteiro.io.workforce.domain.WorkAssignment;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkforceAdministrationService {
    private final WorkforceRepository workforce;
    private final AccessAuthorizationService authorization;
    private final TenantContextHolder tenantContext;
    private final AuditEventRecorder audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<EmployeeEmployment> employments(UUID employee, int offset, int limit) {
        return workforce.listEmployments(requireManager(AccessAction.MANAGE_USERS).companyId(), employee, offset, limit);
    }

    @Transactional(readOnly = true)
    public List<WorkforceRepository.AssignmentSelection> assignments(UUID work, LocalDate from, LocalDate through,
                                                                     int offset, int limit) {
        UUID company = requireManager(AccessAction.MANAGE_USERS).companyId();
        if (from.isAfter(through)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER, "Use an ordered assignment period");
        }
        return workforce.listAssignments(company, work, from, through, offset, limit);
    }

    @Transactional(readOnly = true)
    public List<WorkforceRepository.GrantSelection> grants(UUID work, UUID user, int offset, int limit) {
        return workforce.listGrants(requireManager(AccessAction.MANAGE_ROLES).companyId(), work, user, offset, limit);
    }

    @Transactional
    public EmployeeEmployment recordEmployment(UUID employeeId, LocalDate startedOn, LocalDate endedOn) {
        TenantContext tenant = requireManager(AccessAction.MANAGE_USERS);
        requireDates(startedOn, endedOn);
        lockEmployee(tenant.companyId(), employeeId);
        if (workforce.hasEmploymentOverlap(tenant.companyId(), employeeId, startedOn, endedOn)) {
            throw conflict("Employment periods cannot overlap");
        }
        var employment = new EmployeeEmployment(UUID.randomUUID(), employeeId, startedOn, endedOn, 0);
        workforce.insertEmployment(tenant.companyId(), employment, tenant.userId(), clock.instant());
        audit.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.CREATE, "EmployeeEmployment", employment.id(),
                null, Map.of("employeeId", employeeId), null);
        return employment;
    }

    @Transactional
    public EmployeeEmployment endEmployment(UUID employmentId, LocalDate endedOn, long expectedVersion) {
        TenantContext tenant = requireManager(AccessAction.MANAGE_USERS);
        UUID companyId = tenant.companyId();
        EmployeeEmployment current = workforce.findEmployment(companyId, employmentId)
                .orElseThrow(WorkforceAdministrationService::notFound);
        lockEmployee(companyId, current.employeeId());
        requireDates(current.startedOn(), endedOn);
        if (endedOn == null || workforce.hasAssignmentAfter(companyId, employmentId, endedOn)
                || workforce.hasRecordedTimeAfter(companyId, employmentId, null, endedOn)
                || !workforce.endEmployment(companyId, employmentId, endedOn, expectedVersion)) {
            throw conflict("Employment has a conflicting assignment, has ended, or its version has changed");
        }
        audit.recordDirectAction(AuditModule.EMPLOYEES, AuditAction.UPDATE, "EmployeeEmployment", employmentId,
                Map.of("version", expectedVersion), Map.of("version", expectedVersion + 1), null);
        return workforce.findEmployment(companyId, employmentId).orElseThrow();
    }

    @Transactional
    public WorkAssignment recordAssignment(UUID workId, UUID employmentId, LocalDate startedOn, LocalDate endedOn) {
        TenantContext tenant = requireManager(AccessAction.MANAGE_USERS);
        UUID companyId = tenant.companyId();
        requireDates(startedOn, endedOn);
        lockWork(companyId, workId);
        EmployeeEmployment employment = workforce.findEmployment(companyId, employmentId)
                .orElseThrow(WorkforceAdministrationService::notFound);
        lockEmployee(companyId, employment.employeeId());
        EmployeeEmployment current = workforce.findEmployment(companyId, employmentId).orElseThrow();
        return insertAssignment(tenant, workId, current, startedOn, endedOn);
    }

    @Transactional
    public List<WorkAssignment> recordAssignments(UUID workId, List<UUID> employeeIds, LocalDate startedOn) {
        TenantContext tenant = requireManager(AccessAction.MANAGE_USERS);
        UUID companyId = tenant.companyId();
        requireDates(startedOn, null);
        lockWork(companyId, workId);
        // Take employee locks in the same order even for batches sent to different works.
        List<UUID> orderedEmployees = employeeIds.stream().distinct().sorted().toList();
        orderedEmployees.forEach(employeeId -> lockEmployee(companyId, employeeId));
        return orderedEmployees.stream()
                .map(employeeId -> recordOpenAssignment(tenant, workId, employeeId, startedOn))
                .toList();
    }

    private WorkAssignment recordOpenAssignment(TenantContext tenant, UUID workId, UUID employeeId, LocalDate startedOn) {
        UUID companyId = tenant.companyId();
        var position = workforce.findPosition(companyId, workId, employeeId, startedOn)
                .orElseThrow(() -> assignmentConflict("EMPLOYMENT_REQUIRED", employeeId,
                        "Employee needs a recorded employment covering the assignment start"));
        if (position.assignmentId() != null) {
            WorkAssignment existing = workforce.findAssignment(companyId, position.assignmentId()).orElseThrow();
            if (existing.startedOn().equals(startedOn) && existing.endedOn() == null) {
                return existing;
            }
        }
        EmployeeEmployment employment = workforce.findEmployment(companyId, position.employmentId()).orElseThrow();
        if (!employment.covers(startedOn, null)) {
            throw assignmentConflict("OPEN_EMPLOYMENT_REQUIRED", employeeId,
                    "An open assignment requires an employment without an end date");
        }
        return insertAssignment(tenant, workId, employment, startedOn, null);
    }

    private WorkAssignment insertAssignment(TenantContext tenant, UUID workId, EmployeeEmployment current,
                                            LocalDate startedOn, LocalDate endedOn) {
        UUID companyId = tenant.companyId();
        if (!current.covers(startedOn, endedOn)
                || workforce.hasAssignmentOverlap(companyId, current.employeeId(), startedOn, endedOn)) {
            throw assignmentConflict("ASSIGNMENT_PERIOD_CONFLICT", current.employeeId(),
                    "Assignment must fit employment dates and cannot overlap another assignment");
        }
        var assignment = new WorkAssignment(UUID.randomUUID(), workId, current.employeeId(), current.id(),
                startedOn, endedOn, 0);
        workforce.insertAssignment(companyId, assignment, tenant.userId(), clock.instant());
        audit.recordDirectAction(AuditModule.WORKS, AuditAction.CREATE, "WorkAssignment", assignment.id(),
                null, Map.of("workId", workId, "employmentId", current.id()), null);
        return assignment;
    }

    private static ApiException assignmentConflict(String reason, UUID employeeId, String detail) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, detail,
                List.of(new FieldViolation("employeeIds", employeeId.toString())), reason);
    }

    @Transactional
    public WorkAssignment endAssignment(UUID assignmentId, LocalDate endedOn, long expectedVersion) {
        TenantContext tenant = requireManager(AccessAction.MANAGE_USERS);
        UUID companyId = tenant.companyId();
        WorkAssignment current = workforce.findAssignment(companyId, assignmentId)
                .orElseThrow(WorkforceAdministrationService::notFound);
        lockWork(companyId, current.workId());
        lockEmployee(companyId, current.employeeId());
        requireDates(current.startedOn(), endedOn);
        if (endedOn == null || workforce.hasRecordedTimeAfter(companyId, current.employmentId(), assignmentId, endedOn)
                || !workforce.endAssignment(companyId, assignmentId, endedOn, expectedVersion)) {
            throw conflict("Assignment has ended or its version has changed");
        }
        audit.recordDirectAction(AuditModule.WORKS, AuditAction.UPDATE, "WorkAssignment", assignmentId,
                Map.of("version", expectedVersion), Map.of("version", expectedVersion + 1), null);
        return workforce.findAssignment(companyId, assignmentId).orElseThrow();
    }

    @Transactional
    public WorkAccessGrant grantAccess(UUID workId, UUID userId, WorkOperation operation, Instant startsAt, Instant endsAt) {
        TenantContext tenant = requireManager(AccessAction.MANAGE_ROLES);
        lockWork(tenant.companyId(), workId);
        if (!workforce.isCompanyUser(tenant.companyId(), userId)) {
            throw notFound();
        }
        if (operation == null || startsAt == null || endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, "Invalid access grant window");
        }
        var grant = new WorkAccessGrant(UUID.randomUUID(), workId, userId, operation, startsAt, endsAt, null);
        if (workforce.hasGrantOverlap(tenant.companyId(), grant)) {
            throw conflict("Grants for the same work, user and operation cannot overlap");
        }
        workforce.insertGrant(tenant.companyId(), grant, tenant.userId(), clock.instant());
        audit.recordDirectAction(AuditModule.WORKS, AuditAction.CREATE, "WorkAccessGrant", grant.id(),
                null, Map.of("workId", workId, "userId", userId, "operation", operation.name()), null);
        return grant;
    }

    @Transactional
    public WorkAccessGrant revokeAccess(UUID grantId) {
        TenantContext tenant = requireManager(AccessAction.MANAGE_ROLES);
        WorkAccessGrant current = workforce.findGrant(tenant.companyId(), grantId)
                .orElseThrow(WorkforceAdministrationService::notFound);
        lockWork(tenant.companyId(), current.workId());
        if (workforce.revokeGrant(tenant.companyId(), grantId, clock.instant())) {
            audit.recordDirectAction(AuditModule.WORKS, AuditAction.UPDATE, "WorkAccessGrant", grantId,
                    Map.of("revoked", false), Map.of("revoked", true, "workId", current.workId()), null);
        }
        return workforce.findGrant(tenant.companyId(), grantId).orElseThrow();
    }

    private TenantContext requireManager(AccessAction action) {
        authorization.requirePermission(AccessModule.WORKFORCE, action);
        return tenantContext.requireCurrentTenant();
    }

    private void lockEmployee(UUID companyId, UUID employeeId) {
        if (!workforce.lockEmployee(companyId, employeeId)) {
            throw notFound();
        }
    }

    private void lockWork(UUID companyId, UUID workId) {
        if (!workforce.lockWork(companyId, workId)) {
            throw notFound();
        }
    }

    private static void requireDates(LocalDate startedOn, LocalDate endedOn) {
        if (startedOn == null || endedOn != null && endedOn.isBefore(startedOn)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, "Invalid workforce dates");
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Workforce record not found");
    }

    private static ApiException conflict(String reason) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, reason);
    }
}
