package com.renovar.canteiro.io.workdiary.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record WorkDay(LocalDate date, ZoneId zone, Instant startsAt, Instant endsAt) {

    public WorkDay {
        Objects.requireNonNull(date);
        Objects.requireNonNull(zone);
        if (!Objects.requireNonNull(startsAt).isBefore(Objects.requireNonNull(endsAt))) {
            throw new IllegalArgumentException("The local work date must have a nonempty time window");
        }
    }

    public static WorkDay of(LocalDate date, ZoneId zone) {
        return new WorkDay(date, zone, date.atStartOfDay(zone).toInstant(),
                date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public long capacityMinutes() {
        return Duration.between(startsAt, endsAt).toMinutes();
    }

    public WorkMinutes duration(int minutes) {
        if (minutes <= 0 || minutes > capacityMinutes()) {
            throw new IllegalArgumentException("Reported minutes must be positive and fit the work date");
        }
        return new WorkMinutes(minutes, null, null);
    }

    public WorkMinutes interval(OffsetDateTime start, OffsetDateTime end, Instant now) {
        Instant startInstant = checkedInstant(start);
        Instant endInstant = checkedInstant(end);
        if (startInstant.isBefore(startsAt) || endInstant.isAfter(endsAt)
                || !startInstant.isBefore(endInstant) || endInstant.isAfter(now)) {
            throw new IllegalArgumentException("Reported interval must be elapsed, positive, and within the work date");
        }
        Duration elapsed = Duration.between(startInstant, endInstant);
        if (elapsed.toSecondsPart() != 0) {
            throw new IllegalArgumentException("Elapsed time must be a whole number of minutes");
        }
        return new WorkMinutes(Math.toIntExact(elapsed.toMinutes()),
                startInstant, endInstant);
    }

    public Instant observedStart(OffsetDateTime start, Instant now) {
        if (start == null) {
            throw new IllegalArgumentException("An observation start is required");
        }
        Instant instant = checkedInstant(start);
        if (instant.isBefore(startsAt) || !instant.isBefore(endsAt) || instant.isAfter(now)) {
            throw new IllegalArgumentException("Observation start must be elapsed and within the work date");
        }
        return instant;
    }

    private Instant checkedInstant(OffsetDateTime dateTime) {
        if (!dateTime.equals(dateTime.truncatedTo(ChronoUnit.MINUTES))
                || !zone.getRules().isValidOffset(dateTime.toLocalDateTime(), dateTime.getOffset())) {
            throw new IllegalArgumentException("Reported time must have minute precision and a valid work zone offset");
        }
        return dateTime.toInstant();
    }

    public record WorkMinutes(int minutes, Instant startsAt, Instant endsAt) {
        public WorkMinutes {
            if (minutes <= 0 || (startsAt == null) != (endsAt == null)
                    || startsAt != null && !Duration.between(startsAt, endsAt).equals(Duration.ofMinutes(minutes))) {
                throw new IllegalArgumentException("Invalid minutes or interval");
            }
        }

        public boolean overlaps(WorkMinutes other) {
            return startsAt != null && other.startsAt != null
                    && startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
        }
    }
}
