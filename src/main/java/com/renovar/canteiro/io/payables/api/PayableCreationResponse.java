package com.renovar.canteiro.io.payables.api;

import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description="201 direct creation, 200 idempotent official replay, or 202 pending approval. In 202 payables and financialGroupId are null: no official title/group or balance is created.")
public record PayableCreationResponse(@Schema(types={"string","null"},format="uuid") UUID financialGroupId,
        List<PayableResponse> payables,@Schema(types={"string","null"},format="uuid") UUID changeRequestId,ChangeAuthorizationMode mode) { }
