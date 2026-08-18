package com.renovar.canteiro.io.works.api.response;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;

import java.util.UUID;

public record WorkChangeResponse(WorkResponse work, UUID changeRequestId, ChangeAuthorizationMode mode) {
}
