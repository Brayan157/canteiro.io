package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.governance.domain.ChangeRequest;

import java.util.Map;

public record ChangeRequestListItem(
        ChangeRequest changeRequest,
        ChangeRequestRequesterProjection requester,
        ChangeRequestEntityProjection entity,
        Map<String, Object> previousData,
        Map<String, Object> proposedData,
        Map<String, Object> proposedSummary
) {
}
