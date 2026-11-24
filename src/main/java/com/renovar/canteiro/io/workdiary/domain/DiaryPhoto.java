package com.renovar.canteiro.io.workdiary.domain;

import java.time.Instant;
import java.util.UUID;

public record DiaryPhoto(UUID id, long revision, UUID operationId, String contentType, int sizeBytes, String sha256,
                         UUID uploadedBy, Instant uploadedAt, UUID withdrawnBy, Instant withdrawnAt,
                         String withdrawalReason, Target target) {
    public record Target(UUID activityId, UUID occurrenceId) {
        public boolean hasItem() {
            return activityId != null || occurrenceId != null;
        }

        public boolean hasBoth() {
            return activityId != null && occurrenceId != null;
        }
    }
}
