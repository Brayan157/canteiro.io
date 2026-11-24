package com.renovar.canteiro.io.workdiary.infrastructure;

import com.renovar.canteiro.io.workdiary.domain.DiaryPhoto;
import com.renovar.canteiro.io.workdiary.domain.DiaryPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcDiaryPhotoRepository implements DiaryPhotoRepository {
    private static final String PHOTO_QUERY = """
            SELECT * FROM work_diary_photo WHERE company_id = :company AND work_id = :work AND diary_id = :diary
            """;
    private final JdbcClient jdbc;

    @Override
    public Optional<Day> findDay(UUID company, UUID work, LocalDate date) {
        return jdbc.sql("""
                SELECT d.id, d.current_revision, r.closed
                FROM work_diary d JOIN work_diary_revision r ON r.diary_id = d.id AND r.revision = d.current_revision
                WHERE d.company_id = :company AND d.work_id = :work AND d.performed_on = :date
                """).param("company", company).param("work", work).param("date", date)
                .query((row, index) -> new Day(uuid(row, "id"), row.getLong("current_revision"),
                        row.getBoolean("closed"))).optional();
    }

    @Override
    public boolean revisionExists(UUID company, UUID work, UUID diary, long revision) {
        return scoped("""
                SELECT EXISTS (SELECT 1 FROM work_diary_revision WHERE company_id = :company AND work_id = :work
                    AND diary_id = :diary AND revision = :revision)
                """, company, work, diary).param("revision", revision).query(Boolean.class).single();
    }

    @Override
    public boolean itemExists(UUID company, UUID work, UUID diary, long revision, DiaryPhoto.Target target) {
        String itemQuery = target.activityId() != null
                ? "SELECT 1 FROM work_diary_activity WHERE activity_id = :item"
                : "SELECT 1 FROM work_diary_occurrence WHERE occurrence_id = :item";
        return scoped("SELECT EXISTS (" + itemQuery
                        + " AND company_id = :company AND work_id = :work AND diary_id = :diary AND revision = :revision)",
                company, work, diary).param("revision", revision)
                .param("item", target.activityId() != null ? target.activityId() : target.occurrenceId())
                .query(Boolean.class).single();
    }

    @Override
    public List<DiaryPhoto> list(UUID company, UUID work, UUID diary, Long revision, DiaryPhoto.Target target,
                                 boolean includeWithdrawn, int offset, int limit) {
        String filter = (includeWithdrawn ? "" : " AND withdrawn_at IS NULL")
                + (revision == null ? "" : " AND revision = :revision");
        String itemFilter = target.activityId() != null ? " AND activity_id = :item"
                : target.occurrenceId() != null ? " AND occurrence_id = :item" : "";
        var query = scoped(PHOTO_QUERY + filter + itemFilter + " ORDER BY uploaded_at, id LIMIT :limit OFFSET :offset", company, work, diary)
                .param("offset", offset).param("limit", limit);
        if (revision != null) {
            query.param("revision", revision);
        }
        if (target.hasItem()) {
            query.param("item", target.activityId() != null ? target.activityId() : target.occurrenceId());
        }
        return query.query(this::photo).list();
    }

    @Override
    public Optional<DiaryPhoto> find(UUID company, UUID work, UUID diary, UUID photo) {
        return scoped(PHOTO_QUERY + " AND id = :photo", company, work, diary).param("photo", photo)
                .query(this::photo).optional();
    }

    @Override
    public Optional<DiaryPhoto> findByOperation(UUID company, UUID work, UUID diary, UUID operation) {
        return scoped(PHOTO_QUERY + " AND operation_id = :operation", company, work, diary)
                .param("operation", operation).query(this::photo).optional();
    }

    @Override
    public void insert(UUID company, UUID work, UUID diary, DiaryPhoto photo) {
        scoped("""
                INSERT INTO work_diary_photo (id, company_id, work_id, diary_id, revision, operation_id, content_type,
                    size_bytes, sha256, uploaded_by, uploaded_at, activity_id, occurrence_id)
                VALUES (:id, :company, :work, :diary, :revision, :operation, :type, :size, :sha256, :actor, :at, :activity, :occurrence)
                """, company, work, diary).param("id", photo.id()).param("revision", photo.revision())
                .param("operation", photo.operationId()).param("type", photo.contentType()).param("size", photo.sizeBytes())
                .param("sha256", photo.sha256()).param("actor", photo.uploadedBy())
                .param("activity", photo.target().activityId()).param("occurrence", photo.target().occurrenceId())
                .param("at", photo.uploadedAt().atOffset(ZoneOffset.UTC)).update();
    }

    @Override
    public void withdraw(UUID company, UUID work, UUID diary, UUID photo, UUID actor, Instant at, String reason) {
        scoped("""
                UPDATE work_diary_photo SET withdrawn_at = :at, withdrawn_by = :actor, withdrawal_reason = :reason
                WHERE company_id = :company AND work_id = :work AND diary_id = :diary AND id = :photo
                AND withdrawn_at IS NULL
                """, company, work, diary).param("photo", photo).param("actor", actor)
                .param("at", at.atOffset(ZoneOffset.UTC)).param("reason", reason).update();
    }

    private JdbcClient.StatementSpec scoped(String sql, UUID company, UUID work, UUID diary) {
        return jdbc.sql(sql).param("company", company).param("work", work).param("diary", diary);
    }

    private DiaryPhoto photo(ResultSet row, int index) throws SQLException {
        return new DiaryPhoto(uuid(row, "id"), row.getLong("revision"), uuid(row, "operation_id"),
                row.getString("content_type"), row.getInt("size_bytes"), row.getString("sha256"), uuid(row, "uploaded_by"),
                instant(row, "uploaded_at"), uuid(row, "withdrawn_by"), instant(row, "withdrawn_at"),
                row.getString("withdrawal_reason"), new DiaryPhoto.Target(uuid(row, "activity_id"), uuid(row, "occurrence_id")));
    }

    private static UUID uuid(ResultSet row, String name) throws SQLException {
        return row.getObject(name, UUID.class);
    }

    private static Instant instant(ResultSet row, String name) throws SQLException {
        var value = row.getObject(name, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
