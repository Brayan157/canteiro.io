package com.renovar.canteiro.io.governance.application;

import java.util.UUID;

public record ChangeRequestRequesterProjection(UUID id, String displayName, UUID employeeId, String photoUrl) {
}
