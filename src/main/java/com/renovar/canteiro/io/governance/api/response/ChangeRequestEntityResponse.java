package com.renovar.canteiro.io.governance.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Safe human-readable projection of the record involved in a change request")
public record ChangeRequestEntityResponse(String type, UUID id, String displayName) {
}
