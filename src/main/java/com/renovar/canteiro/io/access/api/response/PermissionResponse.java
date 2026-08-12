package com.renovar.canteiro.io.access.api.response;

import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;

import java.util.UUID;

public record PermissionResponse(
        UUID id,
        AccessModule module,
        AccessAction action,
        String code,
        boolean active
) {
}
