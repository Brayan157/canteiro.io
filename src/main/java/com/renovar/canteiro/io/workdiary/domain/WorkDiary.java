package com.renovar.canteiro.io.workdiary.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.renovar.canteiro.io.workforce.domain.WorkforcePosition;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public record WorkDiary(UUID id, UUID workId, WorkDay day, long revision, boolean closed, String noExecutionReason,
                        String reason, UUID actorId, Instant recordedAt, List<Activity> activities,
                        List<Occurrence> occurrences, List<WeatherCondition> weatherConditions) {

    public WorkDiary {
        activities = List.copyOf(activities);
        occurrences = List.copyOf(occurrences);
        weatherConditions = List.copyOf(weatherConditions);
    }

    public long totalMinutes() {
        return activities.stream().flatMap(activity -> activity.entries().stream())
                .mapToLong(entry -> entry.time().minutes()).sum();
    }

    public record Activity(UUID id, UUID contractId, UUID serviceId, String description, String shift,
                           String front, List<Entry> entries, ActivityTiming timing, List<Participant> participants) {
        public Activity {
            entries = List.copyOf(entries);
            participants = List.copyOf(participants);
        }

        public List<Participant> team() {
            var team = new LinkedHashMap<UUID, Participant>();
            participants.forEach(person -> team.put(person.position().employeeId(), person));
            entries.forEach(entry -> team.putIfAbsent(entry.position().employeeId(),
                    new Participant(entry.position(), entry.occasionalReason())));
            return List.copyOf(team.values());
        }
    }

    public record ActivityTiming(Instant startsAt, Instant endsAt) {
    }

    public record Participant(WorkforcePosition position, String occasionalReason) {
    }

    public record Entry(UUID id, WorkforcePosition position, String occasionalReason, WorkDay.WorkMinutes time) {
    }

    public record Occurrence(UUID id, UUID activityId, String description, String delayReason, Stoppage stoppage,
                             OccurrenceTiming timing) {
    }

    public record OccurrenceTiming(Instant startsAt, Instant endsAt) {
        @JsonProperty("durationMinutes")
        public Long durationMinutes() {
            return endsAt == null ? null : Duration.between(startsAt, endsAt).toMinutes();
        }
    }

    public record Stoppage(Instant startsAt, Instant resumedAt, Instant crewDepartedAt) {
        public Long durationMinutes() {
            Instant limit = resumedAt == null ? crewDepartedAt : resumedAt;
            return limit == null ? null : Duration.between(startsAt, limit).toMinutes();
        }

        public StoppageStatus status() {
            if (resumedAt != null) {
                return StoppageStatus.RESUMED;
            }
            return crewDepartedAt == null ? StoppageStatus.ONGOING : StoppageStatus.CREW_DEPARTED_WITHOUT_RESUMING;
        }
    }

    public enum StoppageStatus {
        ONGOING, RESUMED, CREW_DEPARTED_WITHOUT_RESUMING
    }

    public enum WeatherCondition {
        SUNNY, CLOUDY, RAIN
    }
}
