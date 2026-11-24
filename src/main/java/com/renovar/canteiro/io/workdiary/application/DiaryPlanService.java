package com.renovar.canteiro.io.workdiary.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
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
import com.renovar.canteiro.io.workdiary.domain.DiaryPlanRepository;
import com.renovar.canteiro.io.workdiary.domain.DiaryTimeComparison;
import com.renovar.canteiro.io.workdiary.domain.WorkDay;
import com.renovar.canteiro.io.workdiary.domain.WorkDiary;
import com.renovar.canteiro.io.workdiary.domain.WorkDiaryRepository;
import com.renovar.canteiro.io.workforce.domain.WorkOperation;
import com.renovar.canteiro.io.workforce.domain.WorkforceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryPlanService {
    public static final String ENTITY_TYPE = "WorkDiaryPlan";
    private final DiaryPlanRepository plans;
    private final WorkDiaryRepository diaries;
    private final WorkforceRepository workforce;
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ChangeRequestService requests;
    private final ChangeRequestRepository requestRepository;
    private final AuditEventRecorder audit;
    private final Clock clock;
    private final ObjectMapper mapper;

    @Transactional
    public PlanChangeResponse change(UUID work, LocalDate day, DiaryPlanUpdateRequest content) {
        var tenant = tenants.requireCurrentTenant();
        UUID company = tenant.companyId();
        lockWork(company, work);
        requireScope(tenant, work, WorkOperation.PLAN);
        var mode = authorization.requireChangeAuthorization(AccessModule.WORK_DIARY, ChangeOperation.UPDATE);
        String payload = mapper.writeValueAsString(content);
        var stored = plans.findOperation(company, work, content.operationId());
        if (stored.isPresent()) {
            var prior = stored.orElseThrow();
            if (!prior.date().equals(day) || !prior.actorId().equals(tenant.userId()) || !prior.payload().equals(payload)) {
                throw conflict("Plan operation ID already used");
            }
            return response(prior.version(), prior.requestId(), true, company);
        }
        long current = verifyVersion(company, work, day, content);
        ZoneId zone = zone(company, work);
        validate(company, work, day, zone, content);
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        UUID requestId = null;
        Long version = null;
        if (mode == ChangeAuthorizationMode.DIRECT) {
            version = current + 1;
        } else {
            var proposed = new AuditPayload(Map.of("workId", work.toString(), "workAction", WorkOperation.PLAN.name(),
                    "operationId", content.operationId().toString(), "plannedOn", day.toString(),
                    "zoneId", zone.getId(), "plan", payload));
            var request = requests.create(new CreateChangeRequestCommand(AuditModule.WORK_DIARY, ChangeRequestOperation.UPDATE,
                    ENTITY_TYPE, id, current, new ChangeRequestSnapshot(new AuditPayload(Map.of("version", current)), proposed),
                    content.reason()));
            requestId = request.getId();
        }
        plans.insert(new DiaryPlanRepository.Plan(id, company, work, day, tenant.userId(), content.operationId(), payload,
                zone.getId(), now, requestId, version, version == null ? null : now));
        if (version != null) {
            recordAudit(id, work, day, current, version);
        }
        return response(version, requestId, false, company);
    }

    @Transactional
    public void applyApproved(ChangeRequest request) {
        UUID company = tenants.requireCurrentTenant().companyId();
        var proposal = request.getSnapshot().proposedData().values();
        UUID work = UUID.fromString((String) proposal.get("workId"));
        UUID operation = UUID.fromString((String) proposal.get("operationId"));
        lockWork(company, work);
        requireScope(new TenantContext(request.getRequesterUserId(), company), work, WorkOperation.PLAN);
        if (!authorization.canRequestForTenantUser(AccessModule.WORK_DIARY, ChangeOperation.UPDATE, request.getRequesterUserId())) {
            throw new AccessDeniedException("The requester no longer has the plan request permission");
        }
        var plan = plans.findOperation(company, work, operation)
                .filter(row -> request.getId().equals(row.requestId()) && row.version() == null)
                .orElseThrow(DiaryPlanService::notFound);
        var content = decode(plan);
        long current = verifyVersion(company, work, plan.date(), content);
        ZoneId zone = zone(company, work);
        if (!zone.getId().equals(plan.zoneId())) {
            throw conflict("Work time zone changed after plan submission");
        }
        validate(company, work, plan.date(), zone, content);
        plans.publish(company, work, plan.id(), current + 1, clock.instant());
        recordAudit(plan.id(), work, plan.date(), current, current + 1);
    }

    @Transactional(readOnly = true)
    public ComparisonResponse read(UUID work, LocalDate day, Long planVersion, Long diaryRevision) {
        var tenant = tenants.requireCurrentTenant();
        requireScope(tenant, work, WorkOperation.READ);
        authorization.requirePermission(AccessModule.WORK_DIARY, AccessAction.READ);
        UUID company = tenant.companyId();
        var plan = plans.findOfficial(company, work, day, planVersion).orElse(null);
        if (planVersion != null && plan == null) {
            throw notFound();
        }
        WorkDiary diary = diaries.findCurrent(company, work, day).orElse(null);
        if (diaryRevision != null) {
            if (diary == null) {
                throw notFound();
            }
            diary = diaries.findRevision(company, work, diary.id(), diaryRevision).orElseThrow(DiaryPlanService::notFound);
        }
        Map<UUID, List<WorkDay.WorkMinutes>> reported = new LinkedHashMap<>();
        if (diary != null) {
            for (var activity : diary.activities()) {
                var list = reported.computeIfAbsent(activity.serviceId(), ignored -> new ArrayList<>());
                activity.entries().forEach(entry -> list.add(entry.time()));
            }
        }
        boolean noExecution = diary != null && diary.noExecutionReason() != null;
        var rows = new ArrayList<ScheduleComparisonResponse>();
        if (plan != null) {
            for (var row : decode(plan).schedules()) {
                List<WorkDay.WorkMinutes> minutes = reported.remove(row.serviceId());
                Instant start = row.plannedStart() == null ? null : row.plannedStart().toInstant();
                Instant end = row.plannedEnd() == null ? null : row.plannedEnd().toInstant();
                boolean comparableZone = diary == null || diary.day().zone().getId().equals(plan.zoneId());
                var time = DiaryTimeComparison.compare(row.plannedPersonMinutes(), comparableZone ? start : null,
                        noExecution, minutes == null ? List.of() : minutes);
                rows.add(new ScheduleComparisonResponse(row.serviceId(), row.plannedPersonMinutes(), start, end, time,
                        row.milestones().stream().map(DiaryPlanService::compareMilestone).toList()));
            }
        }
        reported.forEach((service, minutes) -> rows.add(new ScheduleComparisonResponse(service, null, null, null,
                DiaryTimeComparison.compare(null, null, noExecution, minutes), List.of())));
        return new ComparisonResponse(day, plan == null ? null : plan.version(), plan == null ? null : plan.zoneId(),
                diary == null ? null : diary.revision(), diary == null ? null : diary.day().zone().getId(),
                noExecution, rows, diary == null ? List.of() : diary.occurrences());
    }

    private void validate(UUID company, UUID work, LocalDate date, ZoneId zone, DiaryPlanUpdateRequest content) {
        WorkDay day;
        try {
            day = WorkDay.of(date, zone);
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw invalid("Invalid plan date");
        }
        var services = new HashSet<UUID>();
        var milestones = new HashSet<UUID>();
        for (var row : content.schedules()) {
            if (!services.add(row.serviceId())) {
                throw invalid("Only one plan row per service or general work is supported");
            }
            if (row.serviceId() != null && diaries.findContractOfService(company, work, row.serviceId()).isEmpty()) {
                throw notFound();
            }
            if ((row.plannedStart() == null) != (row.plannedEnd() == null)) {
                throw invalid("Both planned interval endpoints are required");
            }
            if (row.plannedStart() != null) {
                try {
                    day.interval(row.plannedStart(), row.plannedEnd(), day.endsAt());
                } catch (IllegalArgumentException exception) {
                    throw invalid(exception.getMessage());
                }
            }
            for (var milestone : row.milestones()) {
                if (!milestones.add(milestone.id())) {
                    throw invalid("Milestone IDs must be unique within the plan");
                }
                validateEvidence(company, work, row.serviceId(), milestone);
            }
        }
    }

    private void validateEvidence(UUID company, UUID work, UUID service, DiaryPlanUpdateRequest.Milestone milestone) {
        LocalDate observedOn = milestone.completionDiaryDate();
        Long observedRevision = milestone.completionDiaryRevision();
        if ((observedOn == null) != (observedRevision == null)) {
            throw invalid("Milestone completion requires a diary date and revision");
        }
        if (observedOn != null) {
            var current = diaries.findCurrent(company, work, observedOn).orElseThrow(DiaryPlanService::notFound);
            var evidence = diaries.findRevision(company, work, current.id(), observedRevision).orElseThrow(DiaryPlanService::notFound);
            if (!evidence.closed() || evidence.activities().stream().noneMatch(activity -> Objects.equals(service, activity.serviceId()))) {
                throw invalid("Milestone completion must reference a closed revision with the matching activity");
            }
        }
    }

    private static MilestoneComparisonResponse compareMilestone(DiaryPlanUpdateRequest.Milestone milestone) {
        LocalDate observed = milestone.completionDiaryDate();
        return new MilestoneComparisonResponse(milestone.id(), milestone.title(), milestone.dueOn(), observed,
                milestone.completionDiaryRevision(), observed == null ? null : Math.max(0, ChronoUnit.DAYS.between(milestone.dueOn(), observed)));
    }

    private long verifyVersion(UUID company, UUID work, LocalDate day, DiaryPlanUpdateRequest content) {
        long current = plans.findOfficial(company, work, day, null).map(DiaryPlanRepository.Plan::version).orElse(0L);
        if (content.expectedVersion() != current) {
            throw conflict("Current official plan version is required");
        }
        return current;
    }

    private PlanChangeResponse response(Long version, UUID requestId, boolean replay, UUID company) {
        var status = requestId == null ? null : requestRepository.findByIdAndCompanyId(requestId, company)
                .orElseThrow(DiaryPlanService::notFound).getStatus();
        return new PlanChangeResponse(version, requestId, status, replay);
    }

    private DiaryPlanUpdateRequest decode(DiaryPlanRepository.Plan plan) {
        return mapper.readValue(plan.payload(), DiaryPlanUpdateRequest.class);
    }

    private void recordAudit(UUID plan, UUID work, LocalDate day, long previous, long current) {
        audit.recordDirectAction(AuditModule.WORK_DIARY, AuditAction.UPDATE, ENTITY_TYPE, plan,
                Map.of("version", previous), Map.of("version", current, "plannedOn", day.toString()),
                Map.of("workId", work.toString()));
    }

    private ZoneId zone(UUID company, UUID work) {
        return diaries.findZone(company, work).orElseThrow(() -> conflict("Configure the work time zone first"));
    }

    private void lockWork(UUID company, UUID work) {
        if (!workforce.lockWork(company, work)) {
            throw notFound();
        }
    }

    private void requireScope(TenantContext tenant, UUID work, WorkOperation operation) {
        if (!workforce.hasGrant(tenant.companyId(), work, tenant.userId(), operation, clock.instant())) {
            throw notFound();
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Plan or work reference not found");
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public record PlanChangeResponse(Long version, UUID changeRequestId, ChangeRequestStatus status, boolean replay) {
    }

    public record ComparisonResponse(LocalDate date, Long planVersion, String planTimeZone, Long diaryRevision,
                                     String diaryTimeZone, boolean explicitNoExecution,
                                     List<ScheduleComparisonResponse> schedules, List<WorkDiary.Occurrence> occurrences) {
    }

    public record ScheduleComparisonResponse(UUID serviceId, Long plannedPersonMinutes, Instant plannedStart, Instant plannedEnd,
                                             DiaryTimeComparison time, List<MilestoneComparisonResponse> milestones) {
    }

    public record MilestoneComparisonResponse(UUID id, String title, LocalDate dueOn, LocalDate completedOn,
                                              Long completionDiaryRevision, Long scheduleDelayDays) {
    }
}
