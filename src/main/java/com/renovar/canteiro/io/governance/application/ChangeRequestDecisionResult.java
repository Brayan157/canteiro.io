package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.governance.domain.ChangeRequest;

public record ChangeRequestDecisionResult(ChangeRequest changeRequest, AppliedChangeRequestEntity appliedEntity) {
}
