package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;

import java.util.UUID;

public record FinancialCancellationResult(UUID entityId, UUID reversalId, String status,
                                          ChangeRequest changeRequest, ChangeAuthorizationMode mode) {
}
