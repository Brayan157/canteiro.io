package com.renovar.canteiro.io.workforce.infrastructure;

import com.renovar.canteiro.io.workforce.domain.EmployeeEmployment;
import com.renovar.canteiro.io.workforce.domain.WorkAccessGrant;
import com.renovar.canteiro.io.workforce.domain.WorkAssignment;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforcePosition;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcWorkforceRepository implements WorkforceRepository {
    private final JdbcClient jdbc;

    public JdbcWorkforceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EmployeeEmployment> listEmployments(UUID company, UUID employee, int offset, int limit) {
        return jdbc.sql("""
                SELECT id, employee_id, started_on, ended_on, version FROM employee_employment
                WHERE company_id = :company AND employee_id = :employee
                ORDER BY started_on DESC, id LIMIT :limit OFFSET :offset
                """).param("company", company).param("employee", employee).param("limit", limit).param("offset", offset)
                .query(this::employment).list();
    }

    @Override
    public List<AssignmentSelection> listAssignments(UUID company, UUID work, LocalDate from, LocalDate through,
                                                      int offset, int limit) {
        return jdbc.sql("""
                SELECT a.*, e.full_name FROM work_assignment a
                JOIN employee e ON e.company_id = a.company_id AND e.id = a.employee_id
                WHERE a.company_id = :company AND a.work_id = :work
                  AND a.started_on <= :through AND (a.ended_on IS NULL OR a.ended_on >= :from)
                ORDER BY a.started_on DESC, a.id LIMIT :limit OFFSET :offset
                """).param("company", company).param("work", work).param("from", from).param("through", through)
                .param("limit", limit).param("offset", offset)
                .query((row, index) -> new AssignmentSelection(assignment(row, index), row.getString("full_name"))).list();
    }

    @Override
    public List<GrantSelection> listGrants(UUID company, UUID work, UUID user, int offset, int limit) {
        return jdbc.sql("""
                SELECT g.*, u.email FROM work_access_grant g
                JOIN app_user u ON u.id = g.user_id
                WHERE g.company_id = :company AND g.work_id = :work
                  AND (CAST(:user AS UUID) IS NULL OR g.user_id = :user)
                ORDER BY g.starts_at DESC, g.id LIMIT :limit OFFSET :offset
                """).param("company", company).param("work", work).param("user", user, Types.OTHER)
                .param("limit", limit).param("offset", offset)
                .query((row, index) -> new GrantSelection(grant(row, index), row.getString("email"))).list();
    }

    @Override
    public Set<WorkOperation> activeOperations(UUID company, UUID work, UUID user, Instant at) {
        return Set.copyOf(jdbc.sql("""
                SELECT g.operation FROM work_access_grant g
                JOIN app_user u ON u.id = g.user_id AND u.status = 'ACTIVE'
                WHERE g.company_id = :company AND g.work_id = :work AND g.user_id = :user
                  AND g.revoked_at IS NULL AND g.starts_at <= :at AND (g.ends_at IS NULL OR g.ends_at > :at)
                """).param("company", company).param("work", work).param("user", user)
                .param("at", at.atOffset(ZoneOffset.UTC))
                .query((row, index) -> WorkOperation.valueOf(row.getString("operation"))).list());
    }

    @Override
    public boolean hasGrant(UUID companyId, UUID workId, UUID userId, WorkOperation operation, Instant at) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM work_access_grant g
                    JOIN app_user u ON u.id = g.user_id AND u.status = 'ACTIVE'
                    WHERE g.company_id = :companyId AND g.work_id = :workId AND g.user_id = :userId
                      AND g.operation = :operation AND g.revoked_at IS NULL AND g.starts_at <= :at
                      AND (g.ends_at IS NULL OR g.ends_at > :at)
                )
                """)
                .param("companyId", companyId)
                .param("workId", workId)
                .param("userId", userId)
                .param("operation", operation.name())
                .param("at", at.atOffset(ZoneOffset.UTC))
                .query(Boolean.class).single();
    }

    @Override
    public Optional<WorkforcePosition> findPosition(UUID companyId, UUID workId, UUID employeeId, LocalDate date) {
        return jdbc.sql("""
                SELECT e.employee_id, e.id AS employment_id,
                       (SELECT a.id FROM work_assignment a
                        WHERE a.company_id = e.company_id AND a.employment_id = e.id AND a.work_id = w.id
                          AND a.started_on <= :date AND (a.ended_on IS NULL OR a.ended_on >= :date)
                       ) AS assignment_id,
                       EXISTS (SELECT 1 FROM work_assignment a
                               WHERE a.company_id = e.company_id AND a.employment_id = e.id AND a.work_id <> w.id
                                 AND a.started_on <= :date AND (a.ended_on IS NULL OR a.ended_on >= :date)) AS assigned_elsewhere
                FROM employee_employment e
                JOIN obra w ON w.company_id = e.company_id AND w.id = :workId
                WHERE e.company_id = :companyId AND e.employee_id = :employeeId AND e.started_on <= :date
                  AND (e.ended_on IS NULL OR e.ended_on >= :date)
                """)
                .param("companyId", companyId)
                .param("workId", workId)
                .param("employeeId", employeeId)
                .param("date", date)
                .query((row, rowNumber) -> new WorkforcePosition(row.getObject("employee_id", UUID.class),
                        row.getObject("employment_id", UUID.class), row.getObject("assignment_id", UUID.class),
                        row.getBoolean("assigned_elsewhere")))
                .optional();
    }

    @Override
    public boolean lockEmployee(UUID companyId, UUID employeeId) {
        return jdbc.sql("SELECT id FROM employee WHERE company_id = :companyId AND id = :employeeId FOR UPDATE")
                .param("companyId", companyId).param("employeeId", employeeId).query(UUID.class).optional().isPresent();
    }

    @Override
    public boolean lockWork(UUID companyId, UUID workId) {
        return jdbc.sql("SELECT id FROM obra WHERE company_id = :companyId AND id = :workId FOR UPDATE")
                .param("companyId", companyId).param("workId", workId).query(UUID.class).optional().isPresent();
    }

    @Override
    public boolean hasEmploymentOverlap(UUID companyId, UUID employeeId, LocalDate from, LocalDate through) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM employee_employment
                    WHERE company_id = :companyId AND employee_id = :employeeId
                      AND daterange(started_on, ended_on, '[]') && daterange(:from, :through, '[]')
                )
                """).param("companyId", companyId).param("employeeId", employeeId)
                .param("from", from).param("through", through).query(Boolean.class).single();
    }

    @Override
    public void insertEmployment(UUID companyId, EmployeeEmployment employment, UUID actorId, Instant recordedAt) {
        jdbc.sql("""
                INSERT INTO employee_employment (id, company_id, employee_id, started_on, ended_on, recorded_by_user_id, recorded_at)
                VALUES (:id, :companyId, :employeeId, :startedOn, :endedOn, :actorId, :recordedAt)
                """).param("id", employment.id()).param("companyId", companyId)
                .param("employeeId", employment.employeeId()).param("startedOn", employment.startedOn())
                .param("endedOn", employment.endedOn()).param("actorId", actorId)
                .param("recordedAt", recordedAt.atOffset(ZoneOffset.UTC)).update();
    }

    @Override
    public Optional<EmployeeEmployment> findEmployment(UUID companyId, UUID employmentId) {
        return jdbc.sql("""
                SELECT id, employee_id, started_on, ended_on, version
                FROM employee_employment WHERE company_id = :companyId AND id = :employmentId
                """).param("companyId", companyId).param("employmentId", employmentId)
                .query(this::employment)
                .optional();
    }

    @Override
    public boolean endEmployment(UUID companyId, UUID employmentId, LocalDate endedOn, long expectedVersion) {
        return jdbc.sql("""
                UPDATE employee_employment SET ended_on = :endedOn, version = version + 1
                WHERE company_id = :companyId AND id = :employmentId AND ended_on IS NULL AND version = :expectedVersion
                """).param("companyId", companyId).param("employmentId", employmentId)
                .param("endedOn", endedOn).param("expectedVersion", expectedVersion).update() == 1;
    }

    @Override
    public boolean hasAssignmentAfter(UUID companyId, UUID employmentId, LocalDate endedOn) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM work_assignment
                    WHERE company_id = :companyId AND employment_id = :employmentId
                      AND (ended_on IS NULL OR ended_on > :endedOn)
                )
                """).param("companyId", companyId).param("employmentId", employmentId)
                .param("endedOn", endedOn).query(Boolean.class).single();
    }

    @Override
    public boolean hasAssignmentOverlap(UUID companyId, UUID employeeId, LocalDate from, LocalDate through) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM work_assignment
                    WHERE company_id = :companyId AND employee_id = :employeeId
                      AND daterange(started_on, ended_on, '[]') && daterange(:from, :through, '[]')
                )
                """).param("companyId", companyId).param("employeeId", employeeId)
                .param("from", from).param("through", through).query(Boolean.class).single();
    }

    @Override
    public void insertAssignment(UUID companyId, WorkAssignment assignment, UUID actorId, Instant recordedAt) {
        jdbc.sql("""
                INSERT INTO work_assignment (id, company_id, work_id, employee_id, employment_id, started_on, ended_on,
                    recorded_by_user_id, recorded_at)
                VALUES (:id, :companyId, :workId, :employeeId, :employmentId, :startedOn, :endedOn, :actorId, :recordedAt)
                """).param("id", assignment.id()).param("companyId", companyId).param("workId", assignment.workId())
                .param("employeeId", assignment.employeeId()).param("employmentId", assignment.employmentId())
                .param("startedOn", assignment.startedOn()).param("endedOn", assignment.endedOn())
                .param("actorId", actorId).param("recordedAt", recordedAt.atOffset(ZoneOffset.UTC)).update();
    }

    @Override
    public Optional<WorkAssignment> findAssignment(UUID companyId, UUID assignmentId) {
        return jdbc.sql("""
                SELECT id, work_id, employee_id, employment_id, started_on, ended_on, version
                FROM work_assignment WHERE company_id = :companyId AND id = :assignmentId
                """).param("companyId", companyId).param("assignmentId", assignmentId)
                .query(this::assignment)
                .optional();
    }

    @Override
    public boolean endAssignment(UUID companyId, UUID assignmentId, LocalDate endedOn, long expectedVersion) {
        return jdbc.sql("""
                UPDATE work_assignment SET ended_on = :endedOn, version = version + 1
                WHERE company_id = :companyId AND id = :assignmentId AND ended_on IS NULL AND version = :expectedVersion
                """).param("companyId", companyId).param("assignmentId", assignmentId)
                .param("endedOn", endedOn).param("expectedVersion", expectedVersion).update() == 1;
    }

    @Override
    public boolean isCompanyUser(UUID companyId, UUID userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM company_user WHERE company_id = :companyId AND user_id = :userId)")
                .param("companyId", companyId).param("userId", userId).query(Boolean.class).single();
    }

    @Override
    public boolean hasGrantOverlap(UUID companyId, WorkAccessGrant grant) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM work_access_grant
                    WHERE company_id = :companyId AND work_id = :workId AND user_id = :userId
                      AND operation = :operation AND revoked_at IS NULL
                      AND tstzrange(starts_at, ends_at, '[)') && tstzrange(:startsAt, :endsAt, '[)'))
                """).param("companyId", companyId).param("workId", grant.workId()).param("userId", grant.userId())
                .param("operation", grant.operation().name()).param("startsAt", grant.startsAt().atOffset(ZoneOffset.UTC))
                .param("endsAt", grant.endsAt() == null ? null : grant.endsAt().atOffset(ZoneOffset.UTC)).query(Boolean.class).single();
    }

    @Override
    public void insertGrant(UUID companyId, WorkAccessGrant grant, UUID actorId, Instant grantedAt) {
        jdbc.sql("""
                INSERT INTO work_access_grant (id, company_id, work_id, user_id, operation, starts_at, ends_at,
                    granted_by_user_id, granted_at)
                VALUES (:id, :companyId, :workId, :userId, :operation, :startsAt, :endsAt, :actorId, :grantedAt)
                """).param("id", grant.id()).param("companyId", companyId).param("workId", grant.workId())
                .param("userId", grant.userId()).param("operation", grant.operation().name())
                .param("startsAt", grant.startsAt().atOffset(ZoneOffset.UTC))
                .param("endsAt", grant.endsAt() == null ? null : grant.endsAt().atOffset(ZoneOffset.UTC))
                .param("actorId", actorId).param("grantedAt", grantedAt.atOffset(ZoneOffset.UTC)).update();
    }

    @Override
    public Optional<WorkAccessGrant> findGrant(UUID companyId, UUID grantId) {
        return jdbc.sql("""
                SELECT id, work_id, user_id, operation, starts_at, ends_at, revoked_at
                FROM work_access_grant WHERE company_id = :companyId AND id = :grantId
                """).param("companyId", companyId).param("grantId", grantId)
                .query(this::grant).optional();
    }

    @Override
    public boolean revokeGrant(UUID companyId, UUID grantId, Instant revokedAt) {
        return jdbc.sql("""
                UPDATE work_access_grant SET revoked_at = :revokedAt
                WHERE company_id = :companyId AND id = :grantId AND revoked_at IS NULL
                """).param("companyId", companyId).param("grantId", grantId)
                .param("revokedAt", revokedAt.atOffset(ZoneOffset.UTC)).update() == 1;
    }

    @Override
    public boolean hasRecordedTimeAfter(UUID companyId, UUID employmentId, UUID assignmentId, LocalDate endedOn) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM work_time_entry e JOIN work_diary d ON e.diary_id = d.id
                    WHERE e.company_id = :companyId AND e.employment_id = :employmentId
                      AND (CAST(:assignmentId AS UUID) IS NULL OR e.assignment_id = :assignmentId)
                      AND d.performed_on > :endedOn)
                """).param("companyId", companyId).param("employmentId", employmentId)
                .param("assignmentId", assignmentId).param("endedOn", endedOn).query(Boolean.class).single();
    }

    private EmployeeEmployment employment(ResultSet row, int index) throws SQLException {
        return new EmployeeEmployment(row.getObject("id", UUID.class), row.getObject("employee_id", UUID.class),
                row.getObject("started_on", LocalDate.class), row.getObject("ended_on", LocalDate.class), row.getLong("version"));
    }

    private WorkAssignment assignment(ResultSet row, int index) throws SQLException {
        return new WorkAssignment(row.getObject("id", UUID.class), row.getObject("work_id", UUID.class),
                row.getObject("employee_id", UUID.class), row.getObject("employment_id", UUID.class),
                row.getObject("started_on", LocalDate.class), row.getObject("ended_on", LocalDate.class), row.getLong("version"));
    }

    private WorkAccessGrant grant(ResultSet row, int index) throws SQLException {
        var end = row.getObject("ends_at", OffsetDateTime.class);
        var revocation = row.getObject("revoked_at", OffsetDateTime.class);
        return new WorkAccessGrant(row.getObject("id", UUID.class), row.getObject("work_id", UUID.class),
                row.getObject("user_id", UUID.class), WorkOperation.valueOf(row.getString("operation")),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                end == null ? null : end.toInstant(), revocation == null ? null : revocation.toInstant());
    }
}
