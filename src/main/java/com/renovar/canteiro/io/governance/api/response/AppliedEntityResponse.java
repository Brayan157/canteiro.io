package com.renovar.canteiro.io.governance.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Record created or applied by an approved change request, when available")
public record AppliedEntityResponse(String type, UUID id, String displayName) {
}
