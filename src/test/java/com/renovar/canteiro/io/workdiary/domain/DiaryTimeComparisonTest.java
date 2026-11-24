package com.renovar.canteiro.io.workdiary.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiaryTimeComparisonTest {
    private static final Instant PLANNED = Instant.parse("2026-10-01T11:00:00Z");

    @Test
    void countsSimultaneousIntervalsOnceForActiveTimeAndPerPersonForLabor() {
        var one = interval(30, 90);
        var two = interval(45, 105);
        var three = interval(150, 180);
        var result = DiaryTimeComparison.compare(120L, PLANNED, false, List.of(one, two, three));
        assertThat(result.actualPersonMinutes()).isEqualTo(150L);
        assertThat(result.actualActiveMinutes()).isEqualTo(105L);
        assertThat(result.personMinutesDifference()).isEqualTo(30L);
        assertThat(result.startDelayMinutes()).isEqualTo(30L);
    }

    @Test
    void durationOnlyDoesNotInventStartOrActiveWindow() {
        var result = DiaryTimeComparison.compare(60L, PLANNED, false,
                List.of(interval(30, 60), new WorkDay.WorkMinutes(30, null, null)));
        assertThat(result.actualPersonMinutes()).isEqualTo(60L);
        assertThat(result.personMinutesDifference()).isZero();
        assertThat(result.intervalCoverage()).isEqualTo(DiaryTimeComparison.IntervalCoverage.PARTIAL);
        assertThat(result.actualStart()).isNull();
        assertThat(result.actualActiveMinutes()).isNull();
        assertThat(result.startDelayMinutes()).isNull();
    }

    @Test
    void distinguishesMissingExecutionFromExplicitZeroAndMissingPlannedMinutes() {
        var missing = DiaryTimeComparison.compare(60L, PLANNED, false, List.of());
        assertThat(missing.actualPersonMinutes()).isNull();
        assertThat(missing.personMinutesDifference()).isNull();

        var explicit = DiaryTimeComparison.compare(60L, PLANNED, true, List.of());
        assertThat(explicit.actualPersonMinutes()).isZero();
        assertThat(explicit.personMinutesDifference()).isEqualTo(-60L);
        assertThat(explicit.startDelayMinutes()).isNull();

        var unplanned = DiaryTimeComparison.compare(null, null, false, List.of(interval(30, 90)));
        assertThat(unplanned.actualPersonMinutes()).isEqualTo(60L);
        assertThat(unplanned.personMinutesDifference()).isNull();
    }

    private static WorkDay.WorkMinutes interval(long start, long end) {
        return new WorkDay.WorkMinutes(Math.toIntExact(end - start), PLANNED.plusSeconds(start * 60),
                PLANNED.plusSeconds(end * 60));
    }
}
