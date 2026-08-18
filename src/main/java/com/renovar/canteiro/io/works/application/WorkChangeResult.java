package com.renovar.canteiro.io.works.application;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.works.domain.Work;

public record WorkChangeResult(Work work, ChangeRequest changeRequest, ChangeAuthorizationMode mode) {
}
