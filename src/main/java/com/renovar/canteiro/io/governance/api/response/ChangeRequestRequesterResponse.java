package com.renovar.canteiro.io.governance.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Safe requester projection scoped to the authenticated company; no storage keys or public URLs are exposed")
public record ChangeRequestRequesterResponse(
        UUID id,
        String displayName,
        @Schema(description = "Linked operational employee in the same company, when available") UUID employeeId,
        @Schema(description = "Authenticated employee-photo endpoint; null without a linked employee photo", example = "/api/v1/company/employees/4df8cfbd-293c-4cab-b3ad-29c8a53ad783/photo") String photoUrl
) {
}
