package com.renovar.canteiro.io.workforce.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface WorkforceRepository {
    List<EmployeeEmployment> listEmployments(UUID company, UUID employee, int offset, int limit);

    List<AssignmentSelection> listAssignments(UUID company, UUID work, LocalDate from, LocalDate through, int offset, int limit);

    List<GrantSelection> listGrants(UUID company, UUID work, UUID user, int offset, int limit);

    Set<WorkOperation> activeOperations(UUID company, UUID work, UUID user, Instant at);

    record AssignmentSelection(WorkAssignment assignment, String employeeName) { }

    record GrantSelection(WorkAccessGrant grant, String userLabel) { }

    boolean hasGrant(UUID companyId, UUID workId, UUID userId, WorkOperation operation, Instant at);

    Optional<WorkforcePosition> findPosition(UUID companyId, UUID workId, UUID employeeId, LocalDate date);

    boolean lockEmployee(UUID companyId, UUID employeeId);

    boolean lockWork(UUID companyId, UUID workId);

    boolean hasEmploymentOverlap(UUID companyId, UUID employeeId, LocalDate from, LocalDate through);

    void insertEmployment(UUID companyId, EmployeeEmployment employment, UUID actorId, Instant recordedAt);

    Optional<EmployeeEmployment> findEmployment(UUID companyId, UUID employmentId);

    boolean endEmployment(UUID companyId, UUID employmentId, LocalDate endedOn, long expectedVersion);

    boolean hasAssignmentAfter(UUID companyId, UUID employmentId, LocalDate endedOn);

    boolean hasAssignmentOverlap(UUID companyId, UUID employeeId, LocalDate from, LocalDate through);

    void insertAssignment(UUID companyId, WorkAssignment assignment, UUID actorId, Instant recordedAt);

    Optional<WorkAssignment> findAssignment(UUID companyId, UUID assignmentId);

    boolean endAssignment(UUID companyId, UUID assignmentId, LocalDate endedOn, long expectedVersion);

    boolean isCompanyUser(UUID companyId, UUID userId);

    boolean hasGrantOverlap(UUID companyId, WorkAccessGrant grant);

    void insertGrant(UUID companyId, WorkAccessGrant grant, UUID actorId, Instant grantedAt);

    Optional<WorkAccessGrant> findGrant(UUID companyId, UUID grantId);

    boolean revokeGrant(UUID companyId, UUID grantId, Instant revokedAt);

    boolean hasRecordedTimeAfter(UUID companyId, UUID employmentId, UUID assignmentId, LocalDate endedOn);
}
