package com.renovar.canteiro.io.workdiary.domain;

import java.util.UUID;

public record DiaryOperationReceipt(UUID operationId, UUID actorId, String payloadHash, UUID diaryId,
                                    Long appliedRevision, UUID changeRequestId) {
}
