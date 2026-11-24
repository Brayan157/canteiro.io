package com.renovar.canteiro.io.workdiary.api;

import com.renovar.canteiro.io.workdiary.domain.WorkDiary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkDiaryResponse(UUID id, UUID workId, LocalDate date, String timeZone, Instant dayStartsAt,
                                Instant dayEndsAt, long revision, boolean closed, String noExecutionReason,
                                String reason, UUID actorId, Instant recordedAt, Long totalMinutes,
                                List<ActivityResponse> activities, List<OccurrenceResponse> occurrences,
                                List<WorkDiary.WeatherCondition> weatherConditions) {
    public static WorkDiaryResponse from(WorkDiary diary) {
        long recordedMinutes = diary.totalMinutes();
        Long total = recordedMinutes == 0 && diary.noExecutionReason() == null ? null : recordedMinutes;
        return new WorkDiaryResponse(diary.id(), diary.workId(), diary.day().date(), diary.day().zone().getId(),
                diary.day().startsAt(), diary.day().endsAt(), diary.revision(), diary.closed(), diary.noExecutionReason(),
                diary.reason(), diary.actorId(), diary.recordedAt(), total,
                diary.activities().stream().map(ActivityResponse::from).toList(),
                diary.occurrences().stream().map(OccurrenceResponse::from).toList(), diary.weatherConditions());
    }

    public record ActivityResponse(UUID id, UUID contractId, UUID serviceId, String description, String shift, String front,
                                   List<EntryResponse> entries, TimingResponse timing, List<ParticipantResponse> participants) {
        static ActivityResponse from(WorkDiary.Activity activity) {
            var timing = activity.timing();
            return new ActivityResponse(activity.id(), activity.contractId(), activity.serviceId(), activity.description(),
                    activity.shift(), activity.front(), activity.entries().stream().map(EntryResponse::from).toList(),
                    timing == null ? null : new TimingResponse(timing.startsAt(), timing.endsAt()),
                    activity.team().stream().map(ParticipantResponse::from).toList());
        }
    }

    public record TimingResponse(Instant startsAt, Instant endsAt) {
    }

    public record ParticipantResponse(UUID employeeId, UUID employmentId, UUID assignmentId, String occasionalReason,
                                      boolean assignedElsewhere) {
        static ParticipantResponse from(WorkDiary.Participant participant) {
            var position = participant.position();
            return new ParticipantResponse(position.employeeId(), position.employmentId(), position.assignmentId(),
                    participant.occasionalReason(), position.assignedToOtherWork());
        }
    }

    public record EntryResponse(UUID id, UUID employeeId, UUID employmentId, UUID assignmentId, String occasionalReason,
                                boolean assignedElsewhere, int minutes, Instant startsAt, Instant endsAt) {
        static EntryResponse from(WorkDiary.Entry entry) {
            return new EntryResponse(entry.id(), entry.position().employeeId(), entry.position().employmentId(),
                    entry.position().assignmentId(), entry.occasionalReason(), entry.position().assignedToOtherWork(),
                    entry.time().minutes(), entry.time().startsAt(), entry.time().endsAt());
        }
    }

    public record OccurrenceResponse(UUID id, UUID activityId, String description, String delayReason,
                                     StoppageResponse stoppage, WorkDiary.OccurrenceTiming timing) {
        static OccurrenceResponse from(WorkDiary.Occurrence occurrence) {
            var stoppage = occurrence.stoppage();
            return new OccurrenceResponse(occurrence.id(), occurrence.activityId(), occurrence.description(),
                    occurrence.delayReason(), stoppage == null ? null : new StoppageResponse(stoppage.startsAt(),
                            stoppage.resumedAt(), stoppage.crewDepartedAt(), stoppage.status(), stoppage.durationMinutes()),
                    occurrence.timing());
        }
    }

    public record StoppageResponse(Instant startsAt, Instant resumedAt, Instant crewDepartedAt,
                                   WorkDiary.StoppageStatus status, Long durationMinutes) {
    }
}
