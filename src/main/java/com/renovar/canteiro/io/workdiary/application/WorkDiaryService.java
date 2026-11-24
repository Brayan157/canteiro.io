package com.renovar.canteiro.io.workdiary.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.AuditPresentationService;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.tenancy.domain.TenantContext;
import com.renovar.canteiro.io.workdiary.domain.DiaryOperationReceipt;
import com.renovar.canteiro.io.workdiary.domain.DiaryRevisionSummary;
import com.renovar.canteiro.io.workdiary.domain.WorkDay;
import com.renovar.canteiro.io.workdiary.domain.WorkDiary;
import com.renovar.canteiro.io.workdiary.domain.WorkDiaryRepository;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforcePosition;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class WorkDiaryService {
    public static final String ENTITY_TYPE = "WorkDiary";
    private final WorkDiaryRepository diaries;
    private final WorkforceRepository workforce;
    private final TenantContextHolder tenantContext;
    private final AccessAuthorizationService authorization;
    private final ChangeRequestService changeRequests;
    private final ChangeRequestRepository changeRequestRepository;
    private final AuditEventRecorder audit;
    private final AuditPresentationService auditPresentation;
    private final Clock clock;
    private final ObjectMapper mapper;

    @Transactional
    public String configureZone(UUID workId, String zoneId) {
        authorization.requirePermission(AccessModule.WORKFORCE, AccessAction.MANAGE_USERS);
        var tenant = tenantContext.requireCurrentTenant();
        lockWork(tenant.companyId(), workId);
        if (!ZoneId.getAvailableZoneIds().contains(zoneId)) {
            throw invalid("A valid IANA time zone is required");
        }
        var before = diaries.findZone(tenant.companyId(), workId).map(ZoneId::getId);
        if (before.filter(zoneId::equals).isPresent()) {
            return zoneId;
        }
        if (!diaries.setZoneBeforeFirstDiary(tenant.companyId(), workId, ZoneId.of(zoneId))) {
            throw conflict("Time zone is fixed after the first diary");
        }
        audit.recordDirectAction(AuditModule.WORKS, AuditAction.UPDATE, "WorkDiaryZone", workId,
                before.map(value -> Map.<String, Object>of("zoneId", value)).orElse(null),
                Map.of("zoneId", zoneId), null);
        return zoneId;
    }

    @Transactional(readOnly = true)
    public WorkDiary read(UUID workId, LocalDate date) {
        TenantContext tenant = requireRead(workId);
        return current(tenant.companyId(), workId, date);
    }

    @Transactional(readOnly = true)
    public String zone(UUID workId) {
        TenantContext tenant = requireRead(workId);
        return diaries.findZone(tenant.companyId(), workId).map(ZoneId::getId)
                .orElseThrow(WorkDiaryService::notFound);
    }

    @Transactional(readOnly = true)
    public List<WorkDiaryRepository.EmployeeSelection> employees(UUID workId, LocalDate date, int offset, int limit) {
        TenantContext tenant = requireRead(workId);
        boolean includeOccasional = workforce.hasGrant(tenant.companyId(), workId, tenant.userId(),
                WorkOperation.REGISTER_OCCASIONAL, clock.instant());
        return diaries.selectEmployees(tenant.companyId(), workId, date, includeOccasional, offset, limit);
    }

    @Transactional(readOnly = true)
    public List<WorkDiaryRepository.TimeSummary> summary(UUID workId, LocalDate from, LocalDate through, int offset, int limit) {
        TenantContext tenant = requireRead(workId);
        if (through.isBefore(from)) {
            throw invalid("End date must not precede the start date");
        }
        return diaries.summarize(tenant.companyId(), workId, from, through, offset, limit);
    }

    @Transactional(readOnly = true)
    public List<WorkDiaryRepository.ScopedAuditEvent> audit(UUID workId, int offset, int limit) {
        TenantContext tenant = requireRead(workId);
        authorization.requirePermission(AccessModule.AUDIT, AccessAction.READ);
        return diaries.activityLog(tenant.companyId(), workId, offset, limit).stream()
                .map(row -> new WorkDiaryRepository.ScopedAuditEvent(row.id(), row.entityId(), row.entityType(), row.actorId(),
                        row.action(), row.occurredAt(), auditPresentation.resolveActorDisplayName(
                                tenant.companyId(), row.actorId(), row.actorType(), row.storedActorDisplayName())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DiaryRevisionSummary> history(UUID workId, LocalDate date, long before, int limit) {
        TenantContext tenant = requireRead(workId);
        var current = current(tenant.companyId(), workId, date);
        return diaries.findRevisionSummaries(tenant.companyId(), workId, current.id(), before, limit);
    }

    @Transactional(readOnly = true)
    public WorkDiary revision(UUID workId, LocalDate date, long revision) {
        TenantContext tenant = requireRead(workId);
        var current = current(tenant.companyId(), workId, date);
        return diaries.findRevision(tenant.companyId(), workId, current.id(), revision)
                .orElseThrow(WorkDiaryService::notFound);
    }

    @Transactional
    public Result change(DiaryChange.Command command) {
        var tenant = tenantContext.requireCurrentTenant();
        UUID companyId = tenant.companyId();
        lockWork(companyId, command.workId());
        requireScope(tenant, command.workId(), command.operation());
        ChangeOperation operation = changeOperation(command);
        ChangeAuthorizationMode mode = authorization.requireChangeAuthorization(AccessModule.WORK_DIARY, operation);
        String hash = fingerprint(command);
        var previous = diaries.findOperation(companyId, command.workId(), command.change().operationId());
        if (previous.isPresent()) {
            var receipt = previous.orElseThrow();
            if (!receipt.actorId().equals(tenant.userId()) || !receipt.payloadHash().equals(hash)) {
                throw conflict("Operation ID already used");
            }
            ChangeRequestStatus status = receipt.changeRequestId() == null ? null
                    : changeRequestRepository.findByIdAndCompanyId(receipt.changeRequestId(), companyId)
                    .orElseThrow(WorkDiaryService::notFound).getStatus();
            return new Result(receipt, status, true);
        }
        var before = diaries.findCurrent(companyId, command.workId(), command.date()).orElse(null);
        UUID diaryId = before == null ? UUID.randomUUID() : before.id();
        WorkDiary proposed = prepare(command, tenant, tenant.userId(), diaryId, before);
        Long appliedRevision = null;
        UUID requestId = null;
        if (mode == ChangeAuthorizationMode.DIRECT) {
            apply(companyId, proposed, before);
            appliedRevision = proposed.revision();
        } else {
            var payload = new AuditPayload(Map.of("workId", command.workId().toString(), "workAction", command.operation().name(),
                    "command", mapper.writeValueAsString(command), "hash", hash, "zoneId", proposed.day().zone().getId()));
            var snapshot = new ChangeRequestSnapshot(before == null ? null : new AuditPayload(auditData(before)), payload);
            var request = changeRequests.create(new CreateChangeRequestCommand(AuditModule.WORK_DIARY,
                    ChangeRequestOperation.valueOf(operation.name()), ENTITY_TYPE, diaryId,
                    before == null ? 0 : before.revision(), snapshot, present(command.change().reason())));
            requestId = request.getId();
        }
        var receipt = new DiaryOperationReceipt(command.change().operationId(), tenant.userId(), hash, diaryId,
                appliedRevision, requestId);
        diaries.saveOperation(companyId, command.workId(), receipt);
        return new Result(receipt, requestId == null ? null : ChangeRequestStatus.PENDING, false);
    }

    @Transactional
    public void applyApproved(ChangeRequest request) {
        var approver = tenantContext.requireCurrentTenant();
        UUID companyId = approver.companyId();
        var data = request.getSnapshot().proposedData().values();
        var command = mapper.readValue((String) data.get("command"), DiaryChange.Command.class);
        var requester = new TenantContext(request.getRequesterUserId(), companyId);
        requireScope(requester, command.workId(), command.operation());
        if (!authorization.canRequestForTenantUser(AccessModule.WORK_DIARY, changeOperation(command), requester.userId())) {
            throw new AccessDeniedException("The requester no longer has the required role permission");
        }
        var receipt = diaries.findOperation(companyId, command.workId(), command.change().operationId())
                .filter(value -> request.getId().equals(value.changeRequestId()) && fingerprint(command).equals(value.payloadHash()))
                .orElseThrow(() -> conflict("Diary proposal no longer matches its operation"));
        var before = diaries.findCurrent(companyId, command.workId(), command.date()).orElse(null);
        if (before != null && !before.id().equals(receipt.diaryId())) {
            throw conflict("The current diary differs from the proposal");
        }
        var proposed = prepare(command, requester, approver.userId(), receipt.diaryId(), before);
        if (!proposed.day().zone().getId().equals(data.get("zoneId"))) {
            throw conflict("The work time zone changed after submission");
        }
        apply(companyId, proposed, before);
        diaries.markApplied(companyId, command.workId(), receipt.operationId(), proposed.revision());
    }

    private WorkDiary prepare(DiaryChange.Command command, TenantContext requester, UUID actorId, UUID diaryId,
                              WorkDiary before) {
        DiaryChange change = command.change();
        boolean create = command.operation() == WorkOperation.CREATE;
        if (create && (before != null || change.expectedRevision() != null)) {
            throw conflict("A diary already exists or an unexpected revision was supplied");
        }
        if (!create && (before == null || !Objects.equals(change.expectedRevision(), before.revision()))) {
            throw conflict("Current diary revision is required");
        }
        WorkDay day = before == null ? newDay(requester.companyId(), command) : before.day();
        long revision = before == null ? 0 : before.revision() + 1;
        String reason = present(change.reason());
        if (!create && reason == null) {
            throw invalid("A reason is required");
        }
        return switch (command.operation()) {
            case CREATE, UPDATE -> content(command, requester, actorId, diaryId, before, day, revision, reason);
            case CLOSE, REOPEN -> transition(command, requester, actorId, Objects.requireNonNull(before), revision, reason);
            default -> throw invalid("Invalid diary operation");
        };
    }

    private WorkDiary content(DiaryChange.Command command, TenantContext requester, UUID actorId, UUID diaryId,
                              WorkDiary before, WorkDay day, long revision, String reason) {
        if (before != null && before.closed()) {
            throw conflict("Reopen the diary before editing its content");
        }
        var change = command.change();
        if (change.activities() == null || change.occurrences() == null || present(change.noExecutionReason()) != null) {
            throw invalid("Activities and occurrences are required for content changes");
        }
        var priorEntries = before == null ? Map.<UUID, WorkDiary.Entry>of() : before.activities().stream()
                .flatMap(activity -> activity.entries().stream()).collect(Collectors.toMap(WorkDiary.Entry::id, entry -> entry));
        var priorActivities = before == null ? Map.<UUID, WorkDiary.Activity>of() : before.activities().stream()
                .collect(Collectors.toMap(WorkDiary.Activity::id, activity -> activity));
        var employeeIds = change.activities().stream().flatMap(activity -> Stream.concat(
                activity.entries().stream().map(DiaryChange.Entry::employeeId),
                activity.participants() == null ? Stream.empty()
                        : activity.participants().stream().map(DiaryChange.Participant::employeeId)))
                .distinct().sorted().toList();
        for (UUID employeeId : employeeIds) {
            if (!workforce.lockEmployee(requester.companyId(), employeeId)) {
                throw notFound();
            }
        }
        var activityIds = new HashSet<UUID>();
        var entryIds = new HashSet<UUID>();
        var byEmployee = new HashMap<UUID, List<WorkDay.WorkMinutes>>();
        var activities = new ArrayList<WorkDiary.Activity>();
        for (var activity : change.activities()) {
            if (!activityIds.add(activity.id())) {
                throw invalid("Activity IDs must be unique");
            }
            UUID contractId = activity.serviceId() == null ? null : diaries.findContractOfService(
                    requester.companyId(), command.workId(), activity.serviceId()).orElseThrow(WorkDiaryService::notFound);
            var entries = new ArrayList<WorkDiary.Entry>();
            for (var entry : activity.entries()) {
                if (!entryIds.add(entry.id())) {
                    throw invalid("Time entry IDs must be unique");
                }
                var fact = resolveEntry(command, requester, day, entry, priorEntries.get(entry.id()));
                var intervals = byEmployee.computeIfAbsent(entry.employeeId(), ignored -> new ArrayList<>());
                validateTime(requester.companyId(), diaryId, entry.employeeId(), day, fact.time(), intervals);
                intervals.add(fact.time());
                entries.add(fact);
            }
            var prior = priorActivities.get(activity.id());
            var timing = activity.timing() == null ? prior == null ? null : prior.timing()
                    : new WorkDiary.ActivityTiming(observedStart(day, activity.timing().start()),
                            observedEnd(day, activity.timing().start(), activity.timing().end()));
            var participants = resolveParticipants(command, requester, day, activity.participants(), prior, entries);
            activities.add(new WorkDiary.Activity(activity.id(), contractId, activity.serviceId(), activity.description(),
                    present(activity.shift()), present(activity.front()), entries, timing, participants));
        }
        var priorOccurrences = before == null ? Map.<UUID, WorkDiary.Occurrence>of() : before.occurrences().stream()
                .collect(Collectors.toMap(WorkDiary.Occurrence::id, occurrence -> occurrence));
        var occurrenceIds = new HashSet<UUID>();
        var occurrences = new ArrayList<WorkDiary.Occurrence>();
        for (var occurrence : change.occurrences()) {
            if (!occurrenceIds.add(occurrence.id()) || occurrence.activityId() != null && !activityIds.contains(occurrence.activityId())) {
                throw invalid("Occurrence IDs must be unique and belong to this diary");
            }
            var prior = priorOccurrences.get(occurrence.id());
            var stoppage = occurrenceStoppage(day, occurrence, prior);
            var timing = occurrence.timing() == null ? prior == null ? null : prior.timing()
                    : new WorkDiary.OccurrenceTiming(observedStart(day, occurrence.timing().start()),
                            observedEnd(day, occurrence.timing().start(), occurrence.timing().end()));
            occurrences.add(new WorkDiary.Occurrence(occurrence.id(), occurrence.activityId(), occurrence.description(),
                    present(occurrence.delayReason()), stoppage, timing));
        }
        var weather = change.weatherConditions() == null ? before == null ? List.<WorkDiary.WeatherCondition>of()
                : before.weatherConditions() : change.weatherConditions();
        if (new HashSet<>(weather).size() != weather.size()) {
            throw invalid("Weather conditions must be unique");
        }
        return new WorkDiary(diaryId, command.workId(), day, revision, false, null, reason,
                actorId, clock.instant(), activities, occurrences, weather);
    }

    private WorkDiary.Stoppage occurrenceStoppage(WorkDay day, DiaryChange.Occurrence requested, WorkDiary.Occurrence prior) {
        if (Boolean.TRUE.equals(requested.clearStoppage())) {
            if (requested.stoppage() != null) {
                throw invalid("Cannot clear and replace a stoppage together");
            }
            return null;
        }
        return requested.stoppage() == null ? prior == null ? null : prior.stoppage() : resolveStoppage(day, requested.stoppage());
    }

    private List<WorkDiary.Participant> resolveParticipants(DiaryChange.Command command, TenantContext requester, WorkDay day,
                List<DiaryChange.Participant> requested, WorkDiary.Activity prior, List<WorkDiary.Entry> entries) {
        if (requested == null) {
            return prior == null ? List.of() : prior.participants();
        }
        var previous = prior == null ? Map.<UUID, WorkDiary.Participant>of() : prior.team().stream()
                .collect(Collectors.toMap(person -> person.position().employeeId(), person -> person));
        var employees = new HashSet<UUID>();
        var resolved = new ArrayList<WorkDiary.Participant>();
        for (var participant : requested) {
            UUID employeeId = participant.employeeId();
            if (!employees.add(employeeId)) {
                throw invalid("Activity participants must be unique");
            }
            String reason = present(participant.occasionalReason());
            var entry = entries.stream().filter(value -> value.position().employeeId().equals(employeeId)).findFirst();
            if (entry.isPresent() && (reason == null || Objects.equals(reason, entry.get().occasionalReason()))) {
                resolved.add(new WorkDiary.Participant(entry.get().position(), entry.get().occasionalReason()));
                continue;
            }
            var known = previous.get(employeeId);
            resolved.add(known != null && Objects.equals(known.occasionalReason(), reason) ? known
                    : new WorkDiary.Participant(resolvePosition(command, requester, day, employeeId, reason), reason));
        }
        return resolved;
    }

    private WorkDiary.Stoppage resolveStoppage(WorkDay day, DiaryChange.Stoppage stoppage) {
        if (stoppage.resumedAt() != null && stoppage.crewDepartedAt() != null) {
            throw invalid("Use either a resumption or a departure without resumption");
        }
        return new WorkDiary.Stoppage(observedStart(day, stoppage.start()),
                observedEnd(day, stoppage.start(), stoppage.resumedAt()),
                observedEnd(day, stoppage.start(), stoppage.crewDepartedAt()));
    }

    private Instant observedStart(WorkDay day, OffsetDateTime start) {
        try {
            return day.observedStart(start, clock.instant());
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private Instant observedEnd(WorkDay day, OffsetDateTime start, OffsetDateTime end) {
        try {
            return end == null ? null : day.interval(start, end, clock.instant()).endsAt();
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private WorkDiary.Entry resolveEntry(DiaryChange.Command command, TenantContext requester, WorkDay day,
                                        DiaryChange.Entry entry, WorkDiary.Entry prior) {
        var time = resolveTime(day, entry);
        String reason = present(entry.occasionalReason());
        if (prior != null && !entry.employeeId().equals(prior.position().employeeId())) {
            throw conflict("The employee of a recorded entry cannot be replaced under the same ID");
        }
        if (prior != null && time.equals(prior.time()) && Objects.equals(reason, prior.occasionalReason())) {
            return prior;
        }
        return new WorkDiary.Entry(entry.id(), resolvePosition(command, requester, day, entry.employeeId(), reason), reason, time);
    }

    private WorkforcePosition resolvePosition(DiaryChange.Command command, TenantContext requester, WorkDay day,
                                              UUID employeeId, String reason) {
        var position = workforce.findPosition(requester.companyId(), command.workId(), employeeId, day.date())
                .orElseThrow(WorkDiaryService::notFound);
        if (position.isOccasional()) {
            requireScope(requester, command.workId(), WorkOperation.REGISTER_OCCASIONAL);
            if (reason == null) {
                throw invalid("Occasional participation requires a reason");
            }
        } else if (reason != null) {
            throw invalid("An assigned employee does not need an occasional participation reason");
        }
        return position;
    }

    private WorkDay.WorkMinutes resolveTime(WorkDay day, DiaryChange.Entry entry) {
        try {
            if (entry.minutes() != null && entry.start() == null && entry.end() == null) {
                return day.duration(entry.minutes());
            }
            if (entry.minutes() == null && entry.start() != null && entry.end() != null) {
                return day.interval(entry.start(), entry.end(), clock.instant());
            }
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        throw invalid("Use either minutes or both interval endpoints");
    }

    private void validateTime(UUID companyId, UUID diaryId, UUID employeeId, WorkDay day,
                              WorkDay.WorkMinutes next, List<WorkDay.WorkMinutes> previous) {
        if (previous.stream().mapToLong(WorkDay.WorkMinutes::minutes).sum() + next.minutes() > day.capacityMinutes()) {
            throw conflict("Reported minutes exceed the work day");
        }
        if (previous.stream().anyMatch(next::overlaps) || diaries.hasCurrentOverlap(companyId, diaryId, employeeId, next)) {
            throw conflict("The employee already has time in the reported interval");
        }
    }

    private WorkDiary transition(DiaryChange.Command command, TenantContext requester, UUID actorId,
                                 WorkDiary before, long revision, String reason) {
        boolean close = command.operation() == WorkOperation.CLOSE;
        if (before.closed() == close) {
            throw conflict("The diary is already in that state");
        }
        String noExecution = present(command.change().noExecutionReason());
        if (command.change().activities() != null || command.change().occurrences() != null
                || command.change().weatherConditions() != null
                || noExecution != null && (!close || !before.activities().isEmpty())) {
            throw invalid("Transition cannot replace diary content");
        }
        if (close && before.activities().isEmpty() && noExecution == null) {
            throw invalid("A day without execution requires an explicit reason");
        }
        if (close && diaries.hasPendingContentChange(requester.companyId(), command.workId(), before.id())) {
            throw conflict("The diary has pending content changes");
        }
        return new WorkDiary(before.id(), before.workId(), before.day(), revision, close, noExecution, reason,
                actorId, clock.instant(), before.activities(), before.occurrences(), before.weatherConditions());
    }

    private void apply(UUID companyId, WorkDiary proposed, WorkDiary before) {
        boolean saved = before == null ? diaries.create(companyId, proposed) : diaries.append(companyId, proposed, before.revision());
        if (!saved) {
            throw conflict("The diary changed concurrently");
        }
        audit.recordDirectAction(AuditModule.WORK_DIARY, before == null ? AuditAction.CREATE : AuditAction.UPDATE,
                ENTITY_TYPE, proposed.id(), before == null ? null : auditData(before), auditData(proposed),
                Map.of("workId", proposed.workId().toString()));
    }

    private static Map<String, Object> auditData(WorkDiary diary) {
        return Map.of("revision", diary.revision(), "closed", diary.closed(), "date", diary.day().date().toString(),
                "totalMinutes", diary.totalMinutes());
    }

    private WorkDay newDay(UUID companyId, DiaryChange.Command command) {
        ZoneId zone = diaries.findZone(companyId, command.workId()).orElseThrow(() -> conflict("Configure the work time zone first"));
        if (command.date().isAfter(LocalDate.now(clock.withZone(zone)))) {
            throw invalid("The diary date cannot be in the future");
        }
        try {
            return WorkDay.of(command.date(), zone);
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalid("Invalid work date");
        }
    }

    private TenantContext requireRead(UUID workId) {
        var tenant = tenantContext.requireCurrentTenant();
        requireScope(tenant, workId, WorkOperation.READ);
        authorization.requirePermission(AccessModule.WORK_DIARY, AccessAction.READ);
        return tenant;
    }

    private WorkDiary current(UUID companyId, UUID workId, LocalDate date) {
        return diaries.findCurrent(companyId, workId, date).orElseThrow(WorkDiaryService::notFound);
    }

    private void requireScope(TenantContext tenant, UUID workId, WorkOperation operation) {
        if (!workforce.hasGrant(tenant.companyId(), workId, tenant.userId(), operation, clock.instant())) {
            throw notFound();
        }
    }

    private void lockWork(UUID companyId, UUID workId) {
        if (!workforce.lockWork(companyId, workId)) {
            throw notFound();
        }
    }

    private String fingerprint(DiaryChange.Command command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(command)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ChangeOperation changeOperation(DiaryChange.Command command) {
        return command.operation() == WorkOperation.CREATE ? ChangeOperation.CREATE : ChangeOperation.UPDATE;
    }

    private static String present(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Diary or work reference not found");
    }

    private static ApiException invalid(String reason) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, reason);
    }

    private static ApiException conflict(String reason) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, reason);
    }

    public record Result(DiaryOperationReceipt receipt, ChangeRequestStatus status, boolean replay) {
    }
}
