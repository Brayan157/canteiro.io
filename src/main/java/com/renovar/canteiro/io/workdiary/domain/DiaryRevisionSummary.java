package com.renovar.canteiro.io.workdiary.domain;

import java.time.Instant;
import java.util.UUID;

public record DiaryRevisionSummary(long revision, boolean closed, UUID actorId, Instant recordedAt) {
}
