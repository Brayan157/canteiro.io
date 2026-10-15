package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementCancellationChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final MeasurementCancellationService service;

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.MEASUREMENTS
                && request.getOperation() == ChangeRequestOperation.CANCEL
                && "Measurement".equals(request.getEntityType());
    }

    @Override
    public void apply(ChangeRequest request) {
        service.applyApprovedCancellation(request);
    }
}
