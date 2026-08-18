package com.renovar.canteiro.io.works.domain;

import lombok.Getter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
public final class Work {
    private final UUID id;
    private final UUID companyId;
    private final UUID finalCustomerId;
    private final String name;
    private final String reference;
    private final WorkExecutionLocationType executionLocationType;
    private final String executionAddress;
    private final WorkStatus status;
    private final LocalDate startedOn;
    private final LocalDate expectedCompletionOn;
    private final LocalDate completedOn;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Work(UUID id, UUID companyId, UUID finalCustomerId, String name, String reference, WorkStatus status,
                 WorkExecutionLocationType executionLocationType, String executionAddress, LocalDate startedOn,
                 LocalDate expectedCompletionOn, LocalDate completedOn, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.companyId = require(companyId, "Work company is required");
        this.finalCustomerId = require(finalCustomerId, "Work final customer is required");
        this.name = requireName(name);
        this.reference = normalize(reference);
        this.executionLocationType = require(executionLocationType, "Work execution location type is required");
        this.executionAddress = validateExecutionAddress(executionLocationType, executionAddress);
        this.status = require(status, "Work status is required");
        validateDates(startedOn, expectedCompletionOn, completedOn);
        this.startedOn = startedOn;
        this.expectedCompletionOn = expectedCompletionOn;
        this.completedOn = completedOn;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    public static Work create(UUID companyId, UUID finalCustomerId, String name, String reference,
                              WorkExecutionLocationType executionLocationType, String executionAddress, WorkStatus status,
                              LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn) {
        return new Work(null, companyId, finalCustomerId, name, reference, status, executionLocationType, executionAddress,
                startedOn, expectedCompletionOn, completedOn, null, null);
    }
    public static Work rehydrate(UUID id, UUID companyId, UUID finalCustomerId, String name, String reference,
                                 WorkExecutionLocationType executionLocationType, String executionAddress, WorkStatus status,
                                 LocalDate startedOn, LocalDate expectedCompletionOn, LocalDate completedOn, Instant createdAt,
                                 Instant updatedAt) {
        return new Work(id, companyId, finalCustomerId, name, reference, status, executionLocationType, executionAddress,
                startedOn, expectedCompletionOn, completedOn, createdAt, updatedAt);
    }
    private static void validateDates(LocalDate startedOn, LocalDate expected, LocalDate completed) {
        if (startedOn != null && expected != null && expected.isBefore(startedOn) || startedOn != null && completed != null && completed.isBefore(startedOn)) throw new IllegalArgumentException("Work dates must not precede its start date");
    }
    private static String requireName(String value) { String n = normalize(value); if (n == null) throw new IllegalArgumentException("Work name is required"); return n; }
    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String validateExecutionAddress(WorkExecutionLocationType type, String address) {
        String normalized = normalize(address);
        if (type == WorkExecutionLocationType.OTHER_ADDRESS && normalized == null) {
            throw new IllegalArgumentException("An address is required for an other execution location");
        }
        return normalized;
    }
    private static <T> T require(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
}
