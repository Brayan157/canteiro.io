package com.renovar.canteiro.io.workdiary.infrastructure;

import com.renovar.canteiro.io.workdiary.domain.DiaryPlanRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcDiaryPlanRepository implements DiaryPlanRepository {
    private final JdbcClient jdbc;

    public JdbcDiaryPlanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Plan> findOfficial(UUID company, UUID work, LocalDate day, Long version) {
        return jdbc.sql("""
                SELECT * FROM work_diary_plan WHERE company_id = :company AND work_id = :work AND planned_on = :day
                    AND official_version IS NOT NULL AND (CAST(:version AS BIGINT) IS NULL OR official_version = :version)
                ORDER BY official_version DESC LIMIT 1
                """).param("company", company).param("work", work).param("day", day).param("version", version)
                .query(this::map).optional();
    }

    @Override
    public Optional<Plan> findOperation(UUID company, UUID work, UUID operation) {
        return jdbc.sql("""
                SELECT * FROM work_diary_plan WHERE company_id = :company AND work_id = :work AND operation_id = :operation
                """).param("company", company).param("work", work).param("operation", operation).query(this::map).optional();
    }

    @Override
    public void insert(Plan plan) {
        jdbc.sql("""
                INSERT INTO work_diary_plan (id, company_id, work_id, planned_on, actor_user_id, operation_id, payload,
                    zone_id, submitted_at, change_request_id, official_version, applied_at)
                VALUES (:id, :company, :work, :day, :actor, :operation, :payload, :zone, :submitted,
                    :request, :version, :applied)
                """).param("id", plan.id()).param("company", plan.companyId()).param("work", plan.workId())
                .param("day", plan.date()).param("actor", plan.actorId()).param("operation", plan.operationId())
                .param("payload", plan.payload()).param("zone", plan.zoneId()).param("submitted", utc(plan.submittedAt()))
                .param("request", plan.requestId()).param("version", plan.version()).param("applied", utc(plan.appliedAt())).update();
    }

    @Override
    public void publish(UUID company, UUID work, UUID id, long version, Instant at) {
        jdbc.sql("""
                UPDATE work_diary_plan SET official_version = :version, applied_at = :at
                WHERE company_id = :company AND work_id = :work AND id = :id AND official_version IS NULL
                """).param("company", company).param("work", work).param("id", id)
                .param("version", version).param("at", utc(at)).update();
    }

    private Plan map(ResultSet row, int index) throws SQLException {
        var applied = row.getObject("applied_at", OffsetDateTime.class);
        return new Plan(row.getObject("id", UUID.class), row.getObject("company_id", UUID.class),
                row.getObject("work_id", UUID.class), row.getObject("planned_on", LocalDate.class),
                row.getObject("actor_user_id", UUID.class), row.getObject("operation_id", UUID.class),
                row.getString("payload"), row.getString("zone_id"),
                row.getObject("submitted_at", OffsetDateTime.class).toInstant(),
                row.getObject("change_request_id", UUID.class), row.getObject("official_version", Long.class),
                applied == null ? null : applied.toInstant());
    }

    private static OffsetDateTime utc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
