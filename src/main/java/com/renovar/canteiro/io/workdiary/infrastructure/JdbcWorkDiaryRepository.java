package com.renovar.canteiro.io.workdiary.infrastructure;

import com.renovar.canteiro.io.governance.domain.AuditActorType;
import com.renovar.canteiro.io.workdiary.domain.DiaryRevisionSummary;
import com.renovar.canteiro.io.workdiary.domain.DiaryOperationReceipt;
import com.renovar.canteiro.io.workdiary.domain.WorkDay;
import com.renovar.canteiro.io.workdiary.domain.WorkDiary;
import com.renovar.canteiro.io.workdiary.domain.WorkDiaryRepository;
import com.renovar.canteiro.io.workforce.domain.WorkforcePosition;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JdbcWorkDiaryRepository implements WorkDiaryRepository {
    private static final String REVISION_QUERY = """
            SELECT d.id, d.work_id, d.performed_on, d.zone_id, d.day_starts_at, d.day_ends_at, r.revision,
                   r.closed, r.no_execution_reason, r.reason, r.actor_user_id, r.recorded_at,
                   array_to_string(r.weather_conditions, ',') AS weather
            FROM work_diary d JOIN work_diary_revision r ON r.diary_id = d.id
            WHERE d.company_id = :company AND d.work_id = :work
            """;
    private final JdbcClient jdbc;

    public JdbcWorkDiaryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ZoneId> findZone(UUID company, UUID work) {
        return scoped("SELECT zone_id FROM work_diary_zone WHERE company_id = :company AND work_id = :work", company, work)
                .query(String.class).optional().map(ZoneId::of);
    }

    @Override
    public boolean setZoneBeforeFirstDiary(UUID company, UUID work, ZoneId zone) {
        return scoped("""
                INSERT INTO work_diary_zone (work_id, company_id, zone_id)
                SELECT :work, :company, :zone WHERE NOT EXISTS
                    (SELECT 1 FROM work_diary WHERE company_id = :company AND work_id = :work)
                ON CONFLICT (work_id) DO UPDATE SET zone_id = EXCLUDED.zone_id
                """, company, work).param("zone", zone.getId()).update() == 1;
    }

    @Override
    public Optional<UUID> findContractOfService(UUID company, UUID work, UUID service) {
        return scoped("""
                SELECT s.contract_id FROM contract_service s JOIN contract c ON c.id = s.contract_id AND c.company_id = s.company_id
                WHERE s.company_id = :company AND c.work_id = :work AND s.id = :service
                """, company, work).param("service", service).query(UUID.class).optional();
    }

    @Override
    public Optional<WorkDiary> findCurrent(UUID company, UUID work, LocalDate date) {
        return scoped(REVISION_QUERY + " AND d.performed_on = :date AND r.revision = d.current_revision", company, work)
                .param("date", date).query(this::shell).optional().map(diary -> loadContent(company, diary));
    }

    @Override
    public Optional<WorkDiary> findRevision(UUID company, UUID work, UUID id, long revision) {
        return scoped(REVISION_QUERY + " AND d.id = :id AND r.revision = :revision", company, work)
                .param("id", id).param("revision", revision).query(this::shell).optional()
                .map(diary -> loadContent(company, diary));
    }

    @Override
    public List<DiaryRevisionSummary> findRevisionSummaries(UUID company, UUID work, UUID id, long before, int limit) {
        return scoped("""
                SELECT revision, closed, actor_user_id, recorded_at FROM work_diary_revision
                WHERE company_id = :company AND work_id = :work AND diary_id = :id AND revision < :before
                ORDER BY revision DESC LIMIT :limit
                """, company, work).param("id", id).param("before", before).param("limit", limit)
                .query((row, index) -> new DiaryRevisionSummary(row.getLong("revision"), row.getBoolean("closed"),
                        uuid(row, "actor_user_id"), instant(row, "recorded_at"))).list();
    }

    @Override
    public boolean hasCurrentOverlap(UUID company, UUID otherThanDiaryId, UUID employee, WorkDay.WorkMinutes time) {
        if (time.startsAt() == null) {
            return false;
        }
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM work_time_effective e
                    WHERE e.company_id = :company AND e.employee_id = :employee AND e.diary_id <> :diary
                      AND e.starts_at < :ends AND e.ends_at > :starts
                )
                """).param("company", company).param("employee", employee).param("diary", otherThanDiaryId)
                .param("starts", utc(time.startsAt())).param("ends", utc(time.endsAt())).query(Boolean.class).single();
    }

    @Override
    public boolean create(UUID company, WorkDiary diary) {
        int inserted = scoped("""
                INSERT INTO work_diary (id, company_id, work_id, performed_on, zone_id, day_starts_at, day_ends_at)
                VALUES (:id, :company, :work, :date, :zone, :starts, :ends)
                ON CONFLICT (company_id, work_id, performed_on) DO NOTHING
                """, company, diary.workId()).param("id", diary.id()).param("date", diary.day().date())
                .param("zone", diary.day().zone().getId()).param("starts", utc(diary.day().startsAt()))
                .param("ends", utc(diary.day().endsAt())).update();
        if (inserted == 1) {
            insertRevision(company, diary);
        }
        return inserted == 1;
    }

    @Override
    public boolean append(UUID company, WorkDiary diary, long expectedRevision) {
        int updated = scoped("""
                UPDATE work_diary SET current_revision = :revision
                WHERE company_id = :company AND work_id = :work AND id = :id AND current_revision = :expected
                """, company, diary.workId()).param("id", diary.id()).param("revision", diary.revision())
                .param("expected", expectedRevision).update();
        if (updated == 1) {
            insertRevision(company, diary);
        }
        return updated == 1;
    }

    @Override
    public Optional<DiaryOperationReceipt> findOperation(UUID company, UUID work, UUID operationId) {
        return scoped("""
                SELECT * FROM work_diary_operation
                WHERE company_id = :company AND work_id = :work AND operation_id = :operation
                """, company, work).param("operation", operationId)
                .query((row, index) -> new DiaryOperationReceipt(uuid(row, "operation_id"), uuid(row, "actor_user_id"),
                        row.getString("payload_hash"), uuid(row, "entity_id"),
                        row.getObject("applied_revision", Long.class), uuid(row, "change_request_id"))).optional();
    }

    @Override
    public void saveOperation(UUID company, UUID work, DiaryOperationReceipt receipt) {
        scoped("""
                INSERT INTO work_diary_operation (company_id, work_id, operation_id, actor_user_id, payload_hash,
                    entity_id, applied_revision, change_request_id)
                VALUES (:company, :work, :operation, :actor, :hash, :entity, :revision, :request)
                """, company, work).param("operation", receipt.operationId()).param("actor", receipt.actorId())
                .param("hash", receipt.payloadHash()).param("entity", receipt.diaryId())
                .param("revision", receipt.appliedRevision()).param("request", receipt.changeRequestId()).update();
    }

    @Override
    public void markApplied(UUID company, UUID work, UUID operationId, long revision) {
        scoped("""
                UPDATE work_diary_operation SET applied_revision = :revision
                WHERE company_id = :company AND work_id = :work AND operation_id = :operation
                """, company, work).param("operation", operationId).param("revision", revision).update();
    }

    @Override
    public boolean hasPendingContentChange(UUID company, UUID work, UUID diary) {
        return scoped("""
                SELECT EXISTS (SELECT 1 FROM work_diary_operation o JOIN change_request r ON r.id = o.change_request_id
                    WHERE o.company_id = :company AND o.work_id = :work AND o.entity_id = :diary
                      AND r.status = 'PENDING' AND r.proposed_data ->> 'workAction' = 'UPDATE')
                """, company, work).param("diary", diary).query(Boolean.class).single();
    }

    @Override
    public List<EmployeeSelection> selectEmployees(UUID company, UUID work, LocalDate date, boolean includeOccasional,
                                                   int offset, int limit) {
        return scoped("""
                SELECT e.id, e.full_name, a.id IS NOT NULL AS assigned
                FROM employee e
                JOIN employee_employment j ON j.company_id = e.company_id AND j.employee_id = e.id
                  AND j.started_on <= :date AND (j.ended_on IS NULL OR j.ended_on >= :date)
                LEFT JOIN work_assignment a ON a.company_id = e.company_id AND a.employment_id = j.id
                  AND a.work_id = :work AND a.started_on <= :date AND (a.ended_on IS NULL OR a.ended_on >= :date)
                WHERE e.company_id = :company AND (a.id IS NOT NULL OR :includeOccasional)
                ORDER BY e.full_name, e.id LIMIT :limit OFFSET :offset
                """, company, work).param("date", date).param("includeOccasional", includeOccasional)
                .param("limit", limit).param("offset", offset)
                .query((row, index) -> new EmployeeSelection(uuid(row, "id"), row.getString("full_name"),
                        row.getBoolean("assigned"))).list();
    }

    @Override
    public List<TimeSummary> summarize(UUID company, UUID work, LocalDate from, LocalDate through, int offset, int limit) {
        return scoped("""
                SELECT employee_id, contract_service_id, SUM(minutes) AS minutes
                FROM work_time_effective
                WHERE company_id = :company AND work_id = :work AND performed_on BETWEEN :from AND :through
                GROUP BY employee_id, contract_service_id ORDER BY employee_id, contract_service_id
                LIMIT :limit OFFSET :offset
                """, company, work).param("from", from).param("through", through).param("offset", offset).param("limit", limit)
                .query((row, index) -> new TimeSummary(uuid(row, "employee_id"), uuid(row, "contract_service_id"),
                        row.getLong("minutes"))).list();
    }

    @Override
    public List<AuditRow> activityLog(UUID company, UUID work, int offset, int limit) {
        return jdbc.sql("""
                SELECT id, entity_id, entity_type, actor_user_id, actor_type, actor_display_name, action, occurred_at FROM audit_event
                WHERE company_id = :company AND module = 'WORK_DIARY' AND metadata ->> 'workId' = :work
                ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset
                """).param("company", company).param("work", work.toString())
                .param("offset", offset).param("limit", limit)
                .query((row, index) -> new AuditRow(uuid(row, "id"), uuid(row, "entity_id"),
                        row.getString("entity_type"), uuid(row, "actor_user_id"), AuditActorType.valueOf(row.getString("actor_type")),
                        row.getString("actor_display_name"), row.getString("action"),
                        instant(row, "occurred_at"))).list();
    }

    private void insertRevision(UUID company, WorkDiary diary) {
        revision("""
                INSERT INTO work_diary_revision (diary_id, revision, company_id, work_id, closed, no_execution_reason,
                    reason, actor_user_id, recorded_at, weather_conditions)
                VALUES (:id, :revision, :company, :work, :closed, :noExecution, :reason, :actor, :recorded,
                    string_to_array(:weather, ','))
                """, company, diary).param("closed", diary.closed()).param("noExecution", diary.noExecutionReason())
                .param("reason", diary.reason()).param("actor", diary.actorId()).param("recorded", utc(diary.recordedAt()))
                .param("weather", diary.weatherConditions().stream().map(Enum::name).collect(Collectors.joining(","))).update();
        for (var activity : diary.activities()) {
            revision("""
                    INSERT INTO work_diary_activity (diary_id, revision, activity_id, company_id, work_id, contract_id,
                        contract_service_id, description, shift_name, front_name, starts_at, ends_at)
                    VALUES (:id, :revision, :activity, :company, :work, :contract, :service, :description, :shift, :front, :start, :end)
                    """, company, diary).param("activity", activity.id()).param("contract", activity.contractId())
                    .param("service", activity.serviceId()).param("description", activity.description())
                    .param("shift", activity.shift()).param("front", activity.front())
                    .param("start", activity.timing() == null ? null : utc(activity.timing().startsAt()))
                    .param("end", activity.timing() == null ? null : utc(activity.timing().endsAt())).update();
            for (var participant : activity.participants()) {
                insertParticipant(company, diary, activity.id(), participant);
            }
            for (var entry : activity.entries()) {
                insertTime(company, diary, activity.id(), entry);
            }
        }
        for (var occurrence : diary.occurrences()) {
            revision("""
                    INSERT INTO work_diary_occurrence (diary_id, revision, occurrence_id, activity_id,
                        company_id, work_id, description, delay_reason, stoppage_starts_at, resumed_at, crew_departed_at,
                        starts_at, ends_at)
                    VALUES (:id, :revision, :occurrence, :activity, :company, :work, :description, :delay, :start, :resumed, :departed,
                        :observedStart, :observedEnd)
                    """, company, diary).param("occurrence", occurrence.id()).param("activity", occurrence.activityId())
                    .param("description", occurrence.description()).param("delay", occurrence.delayReason())
                    .param("start", occurrence.stoppage() == null ? null : utc(occurrence.stoppage().startsAt()))
                    .param("resumed", occurrence.stoppage() == null ? null : utc(occurrence.stoppage().resumedAt()))
                    .param("departed", occurrence.stoppage() == null ? null : utc(occurrence.stoppage().crewDepartedAt()))
                    .param("observedStart", occurrence.timing() == null ? null : utc(occurrence.timing().startsAt()))
                    .param("observedEnd", occurrence.timing() == null ? null : utc(occurrence.timing().endsAt())).update();
        }
    }

    private void insertParticipant(UUID company, WorkDiary diary, UUID activity, WorkDiary.Participant participant) {
        var position = participant.position();
        revision("""
                INSERT INTO work_diary_activity_participant (diary_id, revision, activity_id, company_id, work_id, employee_id,
                    employment_id, assignment_id, occasional_reason, assigned_elsewhere)
                VALUES (:id, :revision, :activity, :company, :work, :employee, :employment, :assignment, :occasional, :elsewhere)
                """, company, diary).param("activity", activity).param("employee", position.employeeId())
                .param("employment", position.employmentId()).param("assignment", position.assignmentId())
                .param("occasional", participant.occasionalReason()).param("elsewhere", position.assignedToOtherWork()).update();
    }

    private void insertTime(UUID company, WorkDiary diary, UUID activity, WorkDiary.Entry entry) {
        var position = entry.position();
        revision("""
                INSERT INTO work_time_entry (diary_id, revision, entry_id, activity_id, company_id, work_id, employee_id,
                    employment_id, assignment_id, occasional_reason, assigned_elsewhere, minutes, starts_at, ends_at)
                VALUES (:id, :revision, :entry, :activity, :company, :work, :employee, :employment, :assignment,
                    :occasional, :elsewhere, :minutes, :starts, :ends)
                """, company, diary).param("entry", entry.id()).param("activity", activity)
                .param("employee", position.employeeId()).param("employment", position.employmentId())
                .param("assignment", position.assignmentId()).param("occasional", entry.occasionalReason())
                .param("elsewhere", position.assignedToOtherWork()).param("minutes", entry.time().minutes())
                .param("starts", utc(entry.time().startsAt())).param("ends", utc(entry.time().endsAt())).update();
    }

    private WorkDiary loadContent(UUID company, WorkDiary diary) {
        Map<UUID, List<TimedActivity>> entries = revision("""
                SELECT * FROM work_time_entry WHERE company_id = :company AND work_id = :work
                AND diary_id = :id AND revision = :revision ORDER BY entry_id
                """, company, diary).query((row, index) -> new TimedActivity(uuid(row, "activity_id"), time(row)))
                .list().stream().collect(Collectors.groupingBy(TimedActivity::activityId));
        var participants = revision("""
                SELECT * FROM work_diary_activity_participant WHERE company_id = :company AND work_id = :work
                AND diary_id = :id AND revision = :revision ORDER BY employee_id
                """, company, diary).query((row, index) -> new ActivityParticipant(uuid(row, "activity_id"),
                        new WorkDiary.Participant(position(row), row.getString("occasional_reason"))))
                .list().stream().collect(Collectors.groupingBy(ActivityParticipant::activityId,
                        Collectors.mapping(ActivityParticipant::participant, Collectors.toList())));
        var activities = revision("""
                SELECT * FROM work_diary_activity WHERE company_id = :company AND work_id = :work
                AND diary_id = :id AND revision = :revision ORDER BY activity_id
                """, company, diary).query((row, index) -> new WorkDiary.Activity(uuid(row, "activity_id"),
                        uuid(row, "contract_id"), uuid(row, "contract_service_id"), row.getString("description"),
                        row.getString("shift_name"), row.getString("front_name"),
                        entries.getOrDefault(uuid(row, "activity_id"), List.of()).stream().map(TimedActivity::entry).toList(),
                        activityTiming(row), participants.getOrDefault(uuid(row, "activity_id"), List.of()))).list();
        var occurrences = revision("""
                SELECT * FROM work_diary_occurrence WHERE company_id = :company AND work_id = :work
                AND diary_id = :id AND revision = :revision ORDER BY occurrence_id
                """, company, diary).query((row, index) -> new WorkDiary.Occurrence(uuid(row, "occurrence_id"),
                        uuid(row, "activity_id"), row.getString("description"), row.getString("delay_reason"), stoppage(row),
                        occurrenceTiming(row))).list();
        return new WorkDiary(diary.id(), diary.workId(), diary.day(), diary.revision(), diary.closed(),
                diary.noExecutionReason(), diary.reason(), diary.actorId(), diary.recordedAt(), activities, occurrences,
                diary.weatherConditions());
    }

    private static WorkDiary.ActivityTiming activityTiming(ResultSet row) throws SQLException {
        Instant start = instant(row, "starts_at");
        return start == null ? null : new WorkDiary.ActivityTiming(start, instant(row, "ends_at"));
    }

    private static WorkDiary.Stoppage stoppage(ResultSet row) throws SQLException {
        Instant start = instant(row, "stoppage_starts_at");
        return start == null ? null : new WorkDiary.Stoppage(start, instant(row, "resumed_at"), instant(row, "crew_departed_at"));
    }

    private static WorkDiary.OccurrenceTiming occurrenceTiming(ResultSet row) throws SQLException {
        Instant start = instant(row, "starts_at");
        return start == null ? null : new WorkDiary.OccurrenceTiming(start, instant(row, "ends_at"));
    }

    private JdbcClient.StatementSpec scoped(String sql, UUID company, UUID work) {
        return jdbc.sql(sql).param("company", company).param("work", work);
    }

    private JdbcClient.StatementSpec revision(String sql, UUID company, WorkDiary diary) {
        return scoped(sql, company, diary.workId()).param("id", diary.id()).param("revision", diary.revision());
    }

    private WorkDiary shell(ResultSet row, int index) throws SQLException {
        var day = new WorkDay(row.getObject("performed_on", LocalDate.class), ZoneId.of(row.getString("zone_id")),
                instant(row, "day_starts_at"), instant(row, "day_ends_at"));
        String weather = row.getString("weather");
        var conditions = weather.isEmpty() ? List.<WorkDiary.WeatherCondition>of()
                : Arrays.stream(weather.split(",")).map(WorkDiary.WeatherCondition::valueOf).toList();
        return new WorkDiary(uuid(row, "id"), uuid(row, "work_id"), day, row.getLong("revision"),
                row.getBoolean("closed"), row.getString("no_execution_reason"), row.getString("reason"),
                uuid(row, "actor_user_id"), instant(row, "recorded_at"), List.of(), List.of(), conditions);
    }

    private WorkDiary.Entry time(ResultSet row) throws SQLException {
        var minutes = new WorkDay.WorkMinutes(row.getInt("minutes"), instant(row, "starts_at"), instant(row, "ends_at"));
        return new WorkDiary.Entry(uuid(row, "entry_id"), position(row), row.getString("occasional_reason"), minutes);
    }

    private static WorkforcePosition position(ResultSet row) throws SQLException {
        return new WorkforcePosition(uuid(row, "employee_id"), uuid(row, "employment_id"),
                uuid(row, "assignment_id"), row.getBoolean("assigned_elsewhere"));
    }

    private static UUID uuid(ResultSet row, String field) throws SQLException {
        return row.getObject(field, UUID.class);
    }

    private static Instant instant(ResultSet row, String field) throws SQLException {
        var value = row.getObject(field, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private record TimedActivity(UUID activityId, WorkDiary.Entry entry) {
    }

    private record ActivityParticipant(UUID activityId, WorkDiary.Participant participant) {
    }
}
