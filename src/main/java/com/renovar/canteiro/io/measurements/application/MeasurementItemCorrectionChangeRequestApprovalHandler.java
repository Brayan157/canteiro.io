package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementItemCorrectionChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {
    private final MeasurementItemCorrectionService service;

    @Override public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.MEASUREMENTS && switch (request.getEntityType()) {
            case "MeasurementItemDraftUpdate", "MeasurementItemDraftCancellation", "MeasurementItemAcceptedCorrection" -> true;
            default -> false;
        };
    }

    @Override public void apply(ChangeRequest request) {
        switch (request.getEntityType()) {
            case "MeasurementItemDraftUpdate" -> service.applyApprovedDraftUpdate(request);
            case "MeasurementItemDraftCancellation" -> service.applyApprovedDraftRemoval(request);
            case "MeasurementItemAcceptedCorrection" -> service.applyApprovedAcceptedCorrection(request);
            default -> throw new IllegalStateException("Unsupported measurement item correction request");
        }
    }
}
