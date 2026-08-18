package com.renovar.canteiro.io.governance.api.response;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import java.util.UUID;
public record ChangeRequestDecisionResponse(UUID id, ChangeRequestStatus status) { }
