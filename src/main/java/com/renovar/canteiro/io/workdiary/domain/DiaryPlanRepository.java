package com.renovar.canteiro.io.workdiary.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface DiaryPlanRepository {
    Optional<Plan> findOfficial(UUID company, UUID work, LocalDate day, Long version);
    Optional<Plan> findOperation(UUID company, UUID work, UUID operation);
    void insert(Plan plan);
    void publish(UUID company, UUID work, UUID id, long version, Instant at);

    record Plan(UUID id, UUID companyId, UUID workId, LocalDate date, UUID actorId, UUID operationId, String payload,
                String zoneId, Instant submittedAt, UUID requestId, Long version, Instant appliedAt) {
    }
}
