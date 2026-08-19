package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementRevisionChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final MeasurementRevisionService measurementRevisionService;

    @Override
    public boolean supports(ChangeRequest changeRequest) {
        return changeRequest.getModule() == AuditModule.MEASUREMENTS
                && changeRequest.getOperation() == ChangeRequestOperation.UPDATE
                && "MeasurementRevision".equals(changeRequest.getEntityType());
    }

    @Override
    public void apply(ChangeRequest changeRequest) {
        measurementRevisionService.applyApprovedCreation(changeRequest);
    }
}
