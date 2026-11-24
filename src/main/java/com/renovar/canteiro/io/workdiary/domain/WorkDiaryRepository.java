package com.renovar.canteiro.io.workdiary.domain;

import com.renovar.canteiro.io.governance.domain.AuditActorType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkDiaryRepository {
    Optional<ZoneId> findZone(UUID companyId, UUID workId);

    boolean setZoneBeforeFirstDiary(UUID companyId, UUID workId, ZoneId zone);

    Optional<UUID> findContractOfService(UUID companyId, UUID workId, UUID serviceId);

    Optional<WorkDiary> findCurrent(UUID companyId, UUID workId, LocalDate date);

    Optional<WorkDiary> findRevision(UUID companyId, UUID workId, UUID diaryId, long revision);

    List<DiaryRevisionSummary> findRevisionSummaries(UUID companyId, UUID workId, UUID diaryId, long before, int limit);

    boolean hasCurrentOverlap(UUID companyId, UUID otherThanDiaryId, UUID employeeId, WorkDay.WorkMinutes time);

    boolean create(UUID companyId, WorkDiary diary);

    boolean append(UUID companyId, WorkDiary diary, long expectedRevision);

    Optional<DiaryOperationReceipt> findOperation(UUID companyId, UUID workId, UUID operationId);

    void saveOperation(UUID companyId, UUID workId, DiaryOperationReceipt receipt);

    void markApplied(UUID companyId, UUID workId, UUID operationId, long revision);

    boolean hasPendingContentChange(UUID companyId, UUID workId, UUID diaryId);

    List<EmployeeSelection> selectEmployees(UUID companyId, UUID workId, LocalDate date, boolean includeOccasional,
                                            int offset, int limit);

    List<TimeSummary> summarize(UUID companyId, UUID workId, LocalDate from, LocalDate through, int offset, int limit);

    List<AuditRow> activityLog(UUID companyId, UUID workId, int offset, int limit);

    record EmployeeSelection(UUID employeeId, String name, boolean assigned) {
    }

    record TimeSummary(UUID employeeId, UUID serviceId, long minutes) {
    }

    record AuditRow(UUID id, UUID entityId, String entityType, UUID actorId, AuditActorType actorType,
                    String storedActorDisplayName, String action, Instant occurredAt) {
    }

    record ScopedAuditEvent(UUID id, UUID entityId, String entityType, UUID actorId, String action, Instant occurredAt,
                            String actorDisplayName) {
    }
}
