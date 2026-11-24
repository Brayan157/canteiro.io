package com.renovar.canteiro.io.workdiary.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WorkDayTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void acceptsMidnightAsAnExclusiveEndButRequiresASeparateContributionAfterIt() {
        WorkDay day = WorkDay.of(LocalDate.parse("2026-11-19"), SAO_PAULO);
        OffsetDateTime start = OffsetDateTime.parse("2026-11-19T23:30-03:00");
        OffsetDateTime end = OffsetDateTime.parse("2026-11-20T00:00-03:00");
        Instant now = Instant.parse("2026-11-20T04:00:00Z");

        assertThat(day.interval(start, end, now).minutes()).isEqualTo(30);
        assertThatIllegalArgumentException().isThrownBy(() -> day.interval(start, end.plusMinutes(1), now));
    }

    @Test
    void distinguishesConfirmedOverlapFromAdjacentIntervalsAndUnknownTimes() {
        WorkDay day = WorkDay.of(LocalDate.parse("2026-11-19"), SAO_PAULO);
        OffsetDateTime start = OffsetDateTime.parse("2026-11-19T08:00-03:00");
        Instant now = Instant.parse("2026-11-19T16:00:00Z");
        var first = day.interval(start, start.plusHours(1), now);

        assertThat(first.overlaps(day.interval(start.plusMinutes(30), start.plusHours(2), now))).isTrue();
        assertThat(first.overlaps(day.interval(start.plusHours(1), start.plusHours(2), now))).isFalse();
        assertThat(first.overlaps(day.duration(60))).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> day.interval(start, start.plusHours(6), now));
    }

    @Test
    void usesElapsedMinutesAndActualDayCapacityAcrossOffsetChanges() {
        ZoneId zone = ZoneId.of("America/New_York");
        WorkDay day = WorkDay.of(LocalDate.parse("2025-11-02"), zone);
        Instant now = Instant.parse("2025-11-03T06:00:00Z");
        OffsetDateTime earlier = OffsetDateTime.parse("2025-11-02T01:30-04:00");
        OffsetDateTime later = OffsetDateTime.parse("2025-11-02T01:30-05:00");

        assertThat(day.capacityMinutes()).isEqualTo(1500);
        assertThat(day.interval(earlier, later, now).minutes()).isEqualTo(60);
        assertThatIllegalArgumentException().isThrownBy(() -> day.duration(1501));
        assertThatIllegalArgumentException().isThrownBy(() ->
                day.interval(OffsetDateTime.parse("2025-11-02T01:30-06:00"), later, now));
    }
}
