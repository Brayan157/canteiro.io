package com.renovar.canteiro.io.workdiary.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record DiaryTimeComparison(Long actualPersonMinutes, Instant actualStart, Instant actualEnd,
                                  Long actualActiveMinutes, IntervalCoverage intervalCoverage,
                                  Long personMinutesDifference, Long startDelayMinutes) {
    public enum IntervalCoverage { ABSENT, PARTIAL, COMPLETE }

    public static DiaryTimeComparison compare(Long plannedPersonMinutes, Instant plannedStart, boolean noExecution,
                                               List<WorkDay.WorkMinutes> minutes) {
        if (minutes.isEmpty()) {
            Long actual = noExecution ? 0L : null;
            return new DiaryTimeComparison(actual, null, null, actual, IntervalCoverage.ABSENT,
                    difference(actual, plannedPersonMinutes), null);
        }

        long personMinutes = minutes.stream().mapToLong(WorkDay.WorkMinutes::minutes).sum();
        Long delta = difference(personMinutes, plannedPersonMinutes);
        var intervals = minutes.stream().filter(time -> time.startsAt() != null)
                .sorted(Comparator.comparing(WorkDay.WorkMinutes::startsAt)).toList();
        if (intervals.size() != minutes.size()) {
            return new DiaryTimeComparison(personMinutes, null, null, null,
                    intervals.isEmpty() ? IntervalCoverage.ABSENT : IntervalCoverage.PARTIAL, delta, null);
        }

        Instant start = intervals.getFirst().startsAt();
        Instant segmentStart = start;
        Instant segmentEnd = intervals.getFirst().endsAt();
        long active = 0;
        for (var interval : intervals.subList(1, intervals.size())) {
            if (interval.startsAt().isAfter(segmentEnd)) {
                active += Duration.between(segmentStart, segmentEnd).toMinutes();
                segmentStart = interval.startsAt();
                segmentEnd = interval.endsAt();
            } else if (interval.endsAt().isAfter(segmentEnd)) {
                segmentEnd = interval.endsAt();
            }
        }
        active += Duration.between(segmentStart, segmentEnd).toMinutes();
        Long delay = plannedStart == null ? null : Math.max(0, Duration.between(plannedStart, start).toMinutes());
        return new DiaryTimeComparison(personMinutes, start, segmentEnd, active, IntervalCoverage.COMPLETE, delta, delay);
    }

    private static Long difference(Long actual, Long planned) {
        return actual == null || planned == null ? null : actual - planned;
    }
}
