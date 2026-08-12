package com.renovar.canteiro.io.access.api.response;

import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;

public record ChangeAuthorizationResponse(
        AccessModule module,
        ChangeOperation operation,
        ChangeAuthorizationMode mode
) {
}
