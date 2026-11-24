package com.renovar.canteiro.io.workforce.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkAccessGrant(UUID id, UUID workId, UUID userId, WorkOperation operation,
                              Instant startsAt, Instant endsAt, Instant revokedAt) {
}
